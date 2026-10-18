#pragma once
#include "vehicle.h"
#include "client.h"
#include "legacy_command_dispatch.h"
#include <optional>
namespace TeslaAndroid {
enum class ExtraKind { CHARGE_SCHEDULE, REMOTE_DRIVE, AUTO_SECURE, LIST_KEYS, PING, TONNEAU, AUTO_SEAT_CLIMATE, DEPLOY_KEY, REGENERATE_KEY };
struct LegacyExtraPlan {ExtraKind kind;int value=0;bool enabled=true;};
inline std::optional<LegacyExtraPlan> plan_legacy_extra(const std::string& operation,
 const std::vector<std::string>& args,const std::string& vin){
 if(args.size()>1)return std::nullopt;
 if(operation=="charging-schedule"){
  int minutes;if(args.size()!=1||!legacy_integer(args[0],0,1439,minutes))return std::nullopt;
  return LegacyExtraPlan{ExtraKind::CHARGE_SCHEDULE,minutes,true};
 }
 if(operation=="charging-schedule-cancel"&&args.empty())return LegacyExtraPlan{ExtraKind::CHARGE_SCHEDULE,0,false};
 if(operation=="drive"&&args.empty())return LegacyExtraPlan{ExtraKind::REMOTE_DRIVE};
 if(operation=="autosecure-modelx"&&args.empty()&&vin.size()==17&&vin[3]=='X')return LegacyExtraPlan{ExtraKind::AUTO_SECURE};
 if(operation=="list-keys"&&args.empty())return LegacyExtraPlan{ExtraKind::LIST_KEYS};
 if(operation=="deploy-key"&&args.empty())return LegacyExtraPlan{ExtraKind::DEPLOY_KEY};
 if(operation=="generate-keys"&&args.empty())return LegacyExtraPlan{ExtraKind::REGENERATE_KEY};
 if(operation=="ping"&&args.empty())return LegacyExtraPlan{ExtraKind::PING};
 if(operation=="tonneau"&&args.size()==1&&vin.size()==17&&vin.rfind("7G2C",0)==0){
  if(args[0]=="open")return LegacyExtraPlan{ExtraKind::TONNEAU,1};
  if(args[0]=="close")return LegacyExtraPlan{ExtraKind::TONNEAU,0};
 }
 if(operation=="auto-seat-and-climate"){
  if(args.empty()||(args.size()==1&&args[0]=="on"))return LegacyExtraPlan{ExtraKind::AUTO_SEAT_CLIMATE,0,true};
  if(args.size()==1&&args[0]=="off")return LegacyExtraPlan{ExtraKind::AUTO_SEAT_CLIMATE,0,false};
 }
 return std::nullopt;
}
inline CarServer_ScheduledChargingAction scheduled_charging_payload(const LegacyExtraPlan& plan){
 CarServer_ScheduledChargingAction value=CarServer_ScheduledChargingAction_init_default;
 value.enabled=plan.enabled;value.charging_time=plan.value;return value;
}
inline VCSEC_ClosureMoveRequest tonneau_payload(const LegacyExtraPlan& plan){
 VCSEC_ClosureMoveRequest value=VCSEC_ClosureMoveRequest_init_default;
 value.tonneau=plan.value?VCSEC_ClosureMoveType_E_CLOSURE_MOVE_TYPE_OPEN:VCSEC_ClosureMoveType_E_CLOSURE_MOVE_TYPE_CLOSE;
 return value;
}
inline bool dispatch_legacy_extra(TeslaBLE::Vehicle& vehicle,const std::string& operation,
 const std::vector<std::string>& args,const std::string& vin,TeslaBLE::Command::OperationResultCallback callback){
 const auto plan=plan_legacy_extra(operation,args,vin);if(!plan)return false;
 if(plan->kind==ExtraKind::REGENERATE_KEY){vehicle.regenerate_key(callback);return true;}
 if(plan->kind==ExtraKind::DEPLOY_KEY){
  if(!vehicle.is_connected()||!vehicle.has_private_key())return false;
  vehicle.pair(Keys_Role_ROLE_OWNER,std::move(callback));return true;
 }
 if(plan->kind==ExtraKind::CHARGE_SCHEDULE){
  const auto action=scheduled_charging_payload(*plan);
  vehicle.send_command_result(UniversalMessage_Domain_DOMAIN_INFOTAINMENT,"Legacy charging schedule",
   [action](TeslaBLE::Client* client,uint8_t* buf,size_t* len){return client->build_car_server_vehicle_action_message(buf,len,CarServer_VehicleAction_scheduledChargingAction_tag,&action);},
   std::move(callback),TeslaBLE::WakePolicy::WAKE_IF_NEEDED);return true;
 }
 if(plan->kind==ExtraKind::REMOTE_DRIVE||plan->kind==ExtraKind::AUTO_SECURE){
  auto action=plan->kind==ExtraKind::REMOTE_DRIVE?VCSEC_RKEAction_E_RKE_ACTION_REMOTE_DRIVE:VCSEC_RKEAction_E_RKE_ACTION_AUTO_SECURE_VEHICLE;
  vehicle.send_command_result(UniversalMessage_Domain_DOMAIN_VEHICLE_SECURITY,"Legacy explicit RKE action",
   [action](TeslaBLE::Client* client,uint8_t* buf,size_t* len){return client->build_vcsec_action_message(action,buf,len);},
   std::move(callback),TeslaBLE::WakePolicy::WAKE_IF_NEEDED);return true;
 }
 if(plan->kind==ExtraKind::LIST_KEYS){
  vehicle.send_command_result(UniversalMessage_Domain_DOMAIN_VEHICLE_SECURITY,"Legacy list keys",
   [](TeslaBLE::Client* client,uint8_t* buf,size_t* len){return client->build_vcsec_information_request_message(VCSEC_InformationRequestType_INFORMATION_REQUEST_TYPE_GET_WHITELIST_INFO,buf,len,0xFFFFFFFF);},
   std::move(callback),TeslaBLE::WakePolicy::NO_WAKE_FAIL);return true;
 }
 if(plan->kind==ExtraKind::PING){
  vehicle.send_command_result(UniversalMessage_Domain_DOMAIN_INFOTAINMENT,"Legacy explicit ping",
   [](TeslaBLE::Client* client,uint8_t* buf,size_t* len){const int32_t value=1;return client->build_car_server_vehicle_action_message(buf,len,CarServer_VehicleAction_ping_tag,&value);},
   std::move(callback),TeslaBLE::WakePolicy::WAKE_IF_NEEDED);return true;
 }
 if(plan->kind==ExtraKind::TONNEAU){
  const auto action=tonneau_payload(*plan);
  vehicle.send_command_result(UniversalMessage_Domain_DOMAIN_VEHICLE_SECURITY,"Legacy tonneau",
   [action](TeslaBLE::Client* client,uint8_t* buf,size_t* len){return client->build_vcsec_closure_message(&action,buf,len);},
   std::move(callback),TeslaBLE::WakePolicy::WAKE_IF_NEEDED);return true;
 }
 if(plan->kind==ExtraKind::AUTO_SEAT_CLIMATE){
  const bool enabled=plan->enabled;
  vehicle.set_auto_seat_climate(TeslaBLE::SeatPosition::FRONT_LEFT,enabled,
   [&vehicle,enabled,callback=std::move(callback)](TeslaBLE::OperationResult left)mutable{
    if(!left.is_success()){callback(std::move(left));return;}
    vehicle.set_auto_seat_climate(TeslaBLE::SeatPosition::FRONT_RIGHT,enabled,
     [&vehicle,enabled,callback=std::move(callback)](TeslaBLE::OperationResult right)mutable{
      if(!right.is_success()){callback(std::move(right));return;}
      vehicle.set_climate(enabled,std::move(callback));
     });
   });return true;
 }
 return false;
}
}
