#include <jni.h>
#include <memory>
#include <string>
#include <vector>
#include <sstream>
#include <iomanip>
#include <cmath>
#include "vehicle.h"
#include "legacy_command_dispatch.h"
#include "legacy_extra_command.h"
#include "legacy_telemetry_json.h"
#include "client.h"
#include "car_server.pb.h"
#include "adapters.h"
#include "vehicle.pb.h"
#include "keys.pb.h"

// All JNI invocations, Java BLE callbacks, and Vehicle.loop() are marshalled onto
// Android's main Looper. The library object must not be accessed concurrently.
namespace {
JNIEnv* current_env = nullptr;
uint64_t command_generation = 0;
std::string configured_vin;
jobject bridge = nullptr;  // global reference to NativeTesla Java object
std::shared_ptr<TeslaBLE::Vehicle> vehicle;
std::shared_ptr<TeslaBLE::BleAdapter> ble;
std::shared_ptr<TeslaBLE::StorageAdapter> storage;

void callText(const char* method, const std::string& message) {
    if (!current_env || !bridge) return;
    jclass cls = current_env->GetObjectClass(bridge);
    jmethodID m = current_env->GetMethodID(cls, method, "(Ljava/lang/String;)V");
    if (m) {
        jstring arg = current_env->NewStringUTF(message.c_str());
        current_env->CallVoidMethod(bridge, m, arg);
        current_env->DeleteLocalRef(arg);
    }
    if(current_env->ExceptionCheck()) current_env->ExceptionClear();
    current_env->DeleteLocalRef(cls);
}
void report(const std::string& s) { callText("onNativeEvent",s); }

class AndroidBle final : public TeslaBLE::BleAdapter {
public:
    bool write(const std::vector<uint8_t>& data) override {
        if (!current_env || !bridge || data.empty()) return false;
        jclass cls = current_env->GetObjectClass(bridge);
        jmethodID method = current_env->GetMethodID(cls, "nativeBleWrite", "([B)Z");
        if (!method) { current_env->DeleteLocalRef(cls);return false; }
        auto array=current_env->NewByteArray(static_cast<jsize>(data.size()));
        current_env->SetByteArrayRegion(array,0,static_cast<jsize>(data.size()),
                                       reinterpret_cast<const jbyte*>(data.data()));
        jboolean accepted=current_env->CallBooleanMethod(bridge,method,array);
        if(current_env->ExceptionCheck()) {current_env->ExceptionClear();accepted=JNI_FALSE;}
        current_env->DeleteLocalRef(array);
        current_env->DeleteLocalRef(cls);
        return accepted==JNI_TRUE;
    }
};
class AndroidStorage final : public TeslaBLE::StorageAdapter {
public:
    bool load(const std::string& key,std::vector<uint8_t>& dest) override {
        if(!current_env||!bridge)return false;
        jclass cls=current_env->GetObjectClass(bridge);
        jmethodID m=current_env->GetMethodID(cls,"storageRead","(Ljava/lang/String;)[B");
        if(!m){current_env->DeleteLocalRef(cls);return false;}
        jstring jkey=current_env->NewStringUTF(key.c_str());
        auto raw=static_cast<jbyteArray>(current_env->CallObjectMethod(bridge,m,jkey));
        if(current_env->ExceptionCheck()){current_env->ExceptionClear();raw=nullptr;}
        bool ok=raw!=nullptr;
        if(ok){
            jsize n=current_env->GetArrayLength(raw);dest.resize(static_cast<size_t>(n));
            if(n)current_env->GetByteArrayRegion(raw,0,n,reinterpret_cast<jbyte*>(dest.data()));
            current_env->DeleteLocalRef(raw);
        }
        current_env->DeleteLocalRef(jkey);current_env->DeleteLocalRef(cls);
        return ok;
    }
    bool save(const std::string& key,const std::vector<uint8_t>& value) override {
        if(!current_env||!bridge)return false;
        jclass cls=current_env->GetObjectClass(bridge);
        jmethodID m=current_env->GetMethodID(cls,"storageWrite","(Ljava/lang/String;[B)Z");
        if(!m){current_env->DeleteLocalRef(cls);return false;}
        jstring jkey=current_env->NewStringUTF(key.c_str());
        jbyteArray data=current_env->NewByteArray(static_cast<jsize>(value.size()));
        if(!value.empty())current_env->SetByteArrayRegion(data,0,static_cast<jsize>(value.size()),reinterpret_cast<const jbyte*>(value.data()));
        jboolean ok=current_env->CallBooleanMethod(bridge,m,jkey,data);
        if(current_env->ExceptionCheck()){current_env->ExceptionClear();ok=JNI_FALSE;}
        current_env->DeleteLocalRef(data);current_env->DeleteLocalRef(jkey);current_env->DeleteLocalRef(cls);
        return ok==JNI_TRUE;
    }
    bool remove(const std::string& key) override {
        if(!current_env||!bridge)return false;
        jclass cls=current_env->GetObjectClass(bridge);
        jmethodID m=current_env->GetMethodID(cls,"storageRemove","(Ljava/lang/String;)Z");
        if(!m){current_env->DeleteLocalRef(cls);return false;}
        jstring jkey=current_env->NewStringUTF(key.c_str());
        jboolean ok=current_env->CallBooleanMethod(bridge,m,jkey);
        if(current_env->ExceptionCheck()){current_env->ExceptionClear();ok=JNI_FALSE;}
        current_env->DeleteLocalRef(jkey);current_env->DeleteLocalRef(cls);
        return ok==JNI_TRUE;
    }
};
std::string chargeJson(const CarServer_ChargeState& c) {
    std::ostringstream s;
    s << "{\"schema_version\":1,\"source\":\"tesla_ble\",\"battery_percent\":";
    if (c.which_optional_battery_level==CarServer_ChargeState_battery_level_tag)
        s << c.optional_battery_level.battery_level;
    else s << "null";
    s << ",\"usable_battery_percent\":";
    if (c.which_optional_usable_battery_level==CarServer_ChargeState_usable_battery_level_tag)
        s << c.optional_usable_battery_level.usable_battery_level;
    else s << "null";
    s << ",\"range_km\":";
    // Tesla reports battery_range in miles. Output is always kilometres.
    if (c.which_optional_battery_range==CarServer_ChargeState_battery_range_tag &&
        std::isfinite(c.optional_battery_range.battery_range))
        s << std::fixed << std::setprecision(2) << c.optional_battery_range.battery_range * 1.609344;
    else s << "null";
    s << ",\"est_range_km\":";
    if (c.which_optional_est_battery_range==CarServer_ChargeState_est_battery_range_tag &&
        std::isfinite(c.optional_est_battery_range.est_battery_range))
        s << std::fixed << std::setprecision(2) << c.optional_est_battery_range.est_battery_range * 1.609344;
    else s << "null";
    s << ",\"charging\":";
    if(c.has_charging_state) {
        int k=c.charging_state.which_type;
        s << ((k==CarServer_ChargeState_ChargingState_Charging_tag) ? "true" : "false");
    } else s << "null";
    s << "}";
    return s.str();
}
void destroy(JNIEnv* env) {
    current_env=env;
    ++command_generation;
    if(vehicle){vehicle->set_connected(false);vehicle.reset();}
    storage.reset();ble.reset();
    if(bridge){env->DeleteGlobalRef(bridge);bridge=nullptr;}
}
} // namespace

extern "C" JNIEXPORT void JNICALL
Java_org_opencharge_teslable_NativeTesla_init(JNIEnv* env,jobject thiz,jstring vin) {
    destroy(env);
    bridge=env->NewGlobalRef(thiz);
    current_env=env;
    const char* chars=env->GetStringUTFChars(vin,nullptr);
    std::string id(chars?chars:"");if(chars)env->ReleaseStringUTFChars(vin,chars);
    ble=std::make_shared<AndroidBle>();storage=std::make_shared<AndroidStorage>();
    vehicle=std::make_shared<TeslaBLE::Vehicle>(ble,storage);
    configured_vin=id;
    vehicle->set_vin(id);
    vehicle->set_charge_state_callback([](const CarServer_ChargeState& c){
        callText("onNativeTelemetry",chargeJson(c));
        callText("onNativeLegacyTelemetry",TeslaAndroid::legacy_charge_json(c));
    });
    vehicle->set_vehicle_status_callback([](const VCSEC_VehicleStatus& c){callText("onNativeLegacyTelemetry",TeslaAndroid::legacy_vcsec_json(c));});
    vehicle->set_climate_state_callback([](const CarServer_ClimateState& c){callText("onNativeLegacyTelemetry",TeslaAndroid::legacy_climate_json(c));});
    vehicle->set_tire_pressure_state_callback([](const CarServer_TirePressureState& c){callText("onNativeLegacyTelemetry",TeslaAndroid::legacy_tyre_json(c));});
    vehicle->set_closures_state_callback([](const CarServer_ClosuresState& c){callText("onNativeLegacyTelemetry",TeslaAndroid::legacy_closure_json(c));});
    vehicle->set_drive_state_callback([](const CarServer_DriveState& c){callText("onNativeLegacyTelemetry",TeslaAndroid::legacy_drive_json(c));});
    vehicle->set_media_state_callback([](const CarServer_MediaState& c,const TeslaBLE::MediaNowPlaying&){callText("onNativeLegacyTelemetry",TeslaAndroid::legacy_media_json(c));});
    vehicle->set_software_update_state_callback([](const CarServer_SoftwareUpdateState& c,const std::string&){callText("onNativeLegacyTelemetry",TeslaAndroid::legacy_software_json(c));});
    report("Native Tesla protocol initialized; enrolled private key: " + std::string(vehicle->has_private_key()?"present":"not yet created"));
}
extern "C" JNIEXPORT void JNICALL
Java_org_opencharge_teslable_NativeTesla_connected(JNIEnv* env,jobject,jboolean active){
    current_env=env;
    if(vehicle)vehicle->set_connected(active==JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_opencharge_teslable_NativeTesla_onNotification(JNIEnv* env,jobject,jbyteArray data){
    current_env=env;
    if(!vehicle||!data)return;
    jsize n=env->GetArrayLength(data);
    std::vector<uint8_t> bytes(static_cast<size_t>(n));
    if(n>0)env->GetByteArrayRegion(data,0,n,reinterpret_cast<jbyte*>(bytes.data()));
    vehicle->on_rx_data(bytes);
}
extern "C" JNIEXPORT void JNICALL
Java_org_opencharge_teslable_NativeTesla_tick(JNIEnv* env,jobject) {
    current_env=env;
    if(vehicle)vehicle->loop();
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_opencharge_teslable_NativeTesla_pairExistingControllerKeyOnOwner(JNIEnv* env,jobject) {
    current_env=env;
    if(!vehicle||!vehicle->is_connected()||!vehicle->has_private_key()) {
        report("Existing-key controller enrollment rejected: matching connected vehicle and existing private key are required");
        return JNI_FALSE;
    }
    report("Locally confirmed Driver-role request for the existing key; physical vehicle approval is required");
    vehicle->pair(Keys_Role_ROLE_DRIVER, [](TeslaBLE::OperationResult result) {
        report(result.is_success()?"Enrollment response accepted; verify vehicle approval and control permissions":
               "Controller enrollment did not complete; no private key was generated or deleted");
    });
    return JNI_TRUE;
}
extern "C" JNIEXPORT void JNICALL
Java_org_opencharge_teslable_NativeTesla_pairMonitorKey(JNIEnv* env,jobject) {
    current_env=env;
    if(!vehicle||!vehicle->is_connected()){report("Connect and enable notifications first");return;}
    report("Requesting read-only Vehicle Monitor key enrollment; approve on Tesla screen with key card");
    vehicle->pair(Keys_Role_ROLE_VEHICLE_MONITOR, [](TeslaBLE::OperationResult result) {
        if(result.is_success()) report("Pairing response accepted; check vehicle key list");
        else report(std::string("Pairing failed: ") +
            (result.error()?result.error()->message():"unknown reason"));
    });
}
extern "C" JNIEXPORT void JNICALL
Java_org_opencharge_teslable_NativeTesla_readChargeState(JNIEnv* env,jobject) {
    current_env=env;
    if(!vehicle||!vehicle->is_connected()){report("Not connected");return;}
    report("Requesting VCSEC status and ChargeState without an explicit vehicle wake");
    // In phase one, never send wake requests from an unattended polling loop.
    vehicle->vcsec_poll(TeslaBLE::WakePolicy::NO_WAKE_SKIP, [](TeslaBLE::OperationResult result) {
        report(result.is_success()?"VCSEC status received":
               result.is_skipped()?"VCSEC status poll skipped":"VCSEC status poll failed");
    });
    vehicle->send_command_result(
        UniversalMessage_Domain_DOMAIN_INFOTAINMENT, "Charge State Read",
        [](TeslaBLE::Client* client, uint8_t* buf, size_t* len) {
            return client->build_car_server_get_vehicle_data_message(buf,len,
                       CarServer_GetVehicleData_getChargeState_tag);
        },
        [](TeslaBLE::OperationResult result) {
            report(result.is_success()?"Charge state response accepted":
                   result.is_skipped()?"Charge state read skipped (vehicle asleep)":
                   std::string("Charge state read failed: ") +
                   (result.error()?result.error()->message():"unknown reason"));
        },
        TeslaBLE::WakePolicy::NO_WAKE_FAIL);
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_opencharge_teslable_NativeTesla_hasPrivateKey(JNIEnv* env,jobject){
    current_env=env;
    return vehicle && vehicle->has_private_key() ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_org_opencharge_teslable_NativeTesla_close(JNIEnv* env,jobject){destroy(env);}

// Only an explicit opt-in service command path may call this JNI entry point.
// It does not subscribe, pair, generate keys, or alter the polling policy.
extern "C" JNIEXPORT jboolean JNICALL
Java_org_opencharge_teslable_NativeTesla_executeLegacyOnOwner(JNIEnv* env,jobject,
        jstring requestId,jstring operation,jobjectArray arguments) {
    current_env=env;
    if(!requestId||!operation||!arguments)return JNI_FALSE;
    auto text=[env](jstring input)->std::string {
        if(!input)return {};
        const char* raw=env->GetStringUTFChars(input,nullptr);
        if(!raw)return {};
        std::string value(raw);env->ReleaseStringUTFChars(input,raw);return value;
    };
    const std::string id=text(requestId);
    if(id.empty()||id.size()>64)return JNI_FALSE;
    for(unsigned char c:id)if(!((c>='a'&&c<='z')||(c>='A'&&c<='Z')
        ||(c>='0'&&c<='9')||c=='_'||c=='-'))return JNI_FALSE;
    auto result=[id](const char* status,bool acknowledged) {
        callText("onNativeCommandResult", "{\"request_id\":\""+id+"\",\"status\":\""
            +status+"\",\"acknowledged\":"+(acknowledged?"true":"false")+"}");
    };
    if(!vehicle){result("unavailable",false);return JNI_FALSE;}
    if(vehicle->pending_command_count()>24){result("busy",false);return JNI_FALSE;}
    const std::string op=text(operation);
    if(op!="generate-keys"&&!vehicle->is_connected()){result("not_connected",false);return JNI_FALSE;}
    const jsize count=env->GetArrayLength(arguments);
    if(count<0||count>1){result("rejected",false);return JNI_FALSE;}
    std::vector<std::string> args;
    for(jsize i=0;i<count;++i){
        auto item=static_cast<jstring>(env->GetObjectArrayElement(arguments,i));
        if(!item){result("rejected",false);return JNI_FALSE;}
        args.push_back(text(item));env->DeleteLocalRef(item);
    }
    const auto generation=command_generation;
    auto complete=[generation,result](TeslaBLE::OperationResult response) {
            if(generation!=command_generation)return;
            result(response.is_success()?"succeeded":response.is_skipped()?"skipped":"failed",
                   response.is_success());
        };
    bool queued=TeslaAndroid::dispatch_legacy_command(*vehicle,op,args,complete);
    if(!queued)queued=TeslaAndroid::dispatch_legacy_extra(*vehicle,op,args,configured_vin,complete);
    if(!queued)result("rejected",false);
    return queued?JNI_TRUE:JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_opencharge_teslable_NativeTesla_readLegacyStateOnOwner(JNIEnv* env,jobject,jstring section) {
    current_env=env;
    if(!section||!vehicle||!vehicle->is_connected()||vehicle->pending_command_count()>16)return JNI_FALSE;
    const char* raw=env->GetStringUTFChars(section,nullptr);if(!raw)return JNI_FALSE;
    std::string selected(raw);env->ReleaseStringUTFChars(section,raw);
    struct Read {const char* name;int tag;};
    const Read reads[]={
        {"charge",CarServer_GetVehicleData_getChargeState_tag},
        {"climate",CarServer_GetVehicleData_getClimateState_tag},
        {"tyre",CarServer_GetVehicleData_getTirePressureState_tag},
        {"closure",CarServer_GetVehicleData_getClosuresState_tag},
        {"drive",CarServer_GetVehicleData_getDriveState_tag},
        {"media",CarServer_GetVehicleData_getMediaState_tag},
        {"software",CarServer_GetVehicleData_getSoftwareUpdateState_tag}
    };
    bool recognized=selected=="all"||selected=="vcsec";
    for(const auto& read:reads)if(selected==read.name)recognized=true;
    if(!recognized)return JNI_FALSE;
    if(selected=="all"||selected=="vcsec")vehicle->vcsec_poll(TeslaBLE::WakePolicy::NO_WAKE_SKIP);
    for(const auto& read:reads){
        if(selected!="all"&&selected!=read.name)continue;
        const int tag=read.tag;
        vehicle->send_command_result(UniversalMessage_Domain_DOMAIN_INFOTAINMENT,
            std::string("Legacy read ")+read.name,
            [tag](TeslaBLE::Client* client,uint8_t* buf,size_t* len){
                return client->build_car_server_get_vehicle_data_message(buf,len,tag);
            }, [](TeslaBLE::OperationResult){}, TeslaBLE::WakePolicy::NO_WAKE_FAIL);
    }
    return JNI_TRUE;
}
