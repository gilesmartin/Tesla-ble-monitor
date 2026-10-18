#include <iostream>
#include <limits>
#include "legacy_telemetry_json.h"
int main(){
 using namespace TeslaAndroid;
 CarServer_ChargeState charge=CarServer_ChargeState_init_default;
 std::cout<<legacy_charge_json(charge)<<'\n';
 charge.which_optional_battery_level=CarServer_ChargeState_battery_level_tag;
 charge.optional_battery_level.battery_level=0;
 charge.which_optional_charger_power=CarServer_ChargeState_charger_power_tag;
 charge.optional_charger_power.charger_power=0;
 charge.which_optional_battery_range=CarServer_ChargeState_battery_range_tag;
 charge.optional_battery_range.battery_range=100.0f;
 charge.which_optional_est_battery_range=CarServer_ChargeState_est_battery_range_tag;
 charge.optional_est_battery_range.est_battery_range=std::numeric_limits<float>::quiet_NaN();
 charge.has_charging_state=true;charge.charging_state.which_type=CarServer_ChargeState_ChargingState_Stopped_tag;
 std::cout<<legacy_charge_json(charge)<<'\n';
 CarServer_ClimateState climate=CarServer_ClimateState_init_default;
 climate.which_optional_is_climate_on=CarServer_ClimateState_is_climate_on_tag;
 climate.optional_is_climate_on.is_climate_on=false;
 climate.which_optional_outside_temp_celsius=CarServer_ClimateState_outside_temp_celsius_tag;
 climate.optional_outside_temp_celsius.outside_temp_celsius=-3.5f;
 std::cout<<legacy_climate_json(climate)<<'\n';
 CarServer_TirePressureState tyre=CarServer_TirePressureState_init_default;
 tyre.which_optional_tpms_pressure_fl=CarServer_TirePressureState_tpms_pressure_fl_tag;
 tyre.optional_tpms_pressure_fl.tpms_pressure_fl=2.45f;
 std::cout<<legacy_tyre_json(tyre)<<'\n';
 CarServer_ClosuresState closure=CarServer_ClosuresState_init_default;
 closure.which_optional_locked=CarServer_ClosuresState_locked_tag;closure.optional_locked.locked=false;
 closure.has_sentry_mode_state=true;closure.sentry_mode_state.which_type=CarServer_ClosuresState_SentryModeState_Off_tag;
 std::cout<<legacy_closure_json(closure)<<'\n';
 CarServer_DriveState drive=CarServer_DriveState_init_default;
 drive.which_optional_odometer_in_hundredths_of_a_mile=CarServer_DriveState_odometer_in_hundredths_of_a_mile_tag;
 drive.optional_odometer_in_hundredths_of_a_mile.odometer_in_hundredths_of_a_mile=1234500;
 drive.has_shift_state=true;drive.shift_state.which_type=CarServer_ShiftState_P_tag;
 std::cout<<legacy_drive_json(drive)<<'\n';
 VCSEC_VehicleStatus vcsec=VCSEC_VehicleStatus_init_default;
 vcsec.vehicleSleepStatus=VCSEC_VehicleSleepStatus_E_VEHICLE_SLEEP_STATUS_ASLEEP;
 vcsec.vehicleLockState=VCSEC_VehicleLockState_E_VEHICLELOCKSTATE_UNLOCKED;
 vcsec.userPresence=VCSEC_UserPresence_E_VEHICLE_USER_PRESENCE_NOT_PRESENT;
 std::cout<<legacy_vcsec_json(vcsec)<<'\n';
 LegacyStateJson escaped("test");escaped.put("text",std::string("quote\" newline\n slash\\"));
 std::cout<<escaped.finish()<<'\n';
}
