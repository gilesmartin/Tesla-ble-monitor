#pragma once
// Presence-sensitive bindings for TeslaBLE 42b899b7816cc3700b7361e65b526fdc04d19a51.
// Internal telemetry only: the established schema-1 local API is unchanged.
#include "vehicle.pb.h"
#include "vcsec.pb.h"
#include "state_text.h"
#include <string>
#include <sstream>
#include <iomanip>
#include <locale>
#include <type_traits>
#include <cmath>
namespace TeslaAndroid {
class LegacyStateJson {
 std::ostringstream values;std::string section;bool first=true;
 static std::string quote(const std::string& value){std::ostringstream s;s<<'"';for(unsigned char c:value){if(c=='"'||c=='\\')s<<'\\'<<c;else if(c<32)s<<"\\u"<<std::hex<<std::setw(4)<<std::setfill('0')<<static_cast<int>(c);else s<<c;}s<<'"';return s.str();}
 void key(const char* name){if(!first)values<<',';first=false;values<<quote(name)<<':';}
 public:
 explicit LegacyStateJson(const char* name):section(name){values.imbue(std::locale::classic());values<<std::boolalpha<<std::setprecision(12);}
 template<class T>void put(const char* name,T value){if constexpr(std::is_floating_point_v<T>){if(!std::isfinite(value))return;}key(name);values<<value;}
 void put(const char* name,const std::string& value){key(name);values<<quote(value);}
 void put(const char* name,const char* value){put(name,std::string(value));}
 std::string finish()const{return "{\"section\":"+quote(section)+",\"values\":{"+values.str()+"}}";}
};
inline std::string legacy_charge_json(const CarServer_ChargeState& value){
 LegacyStateJson out("charge");
 if(value.which_optional_charge_limit_soc==CarServer_ChargeState_charge_limit_soc_tag)out.put("charge_limit_soc",value.optional_charge_limit_soc.charge_limit_soc);
 if(value.which_optional_charge_limit_soc_std==CarServer_ChargeState_charge_limit_soc_std_tag)out.put("charge_limit_soc_std",value.optional_charge_limit_soc_std.charge_limit_soc_std);
 if(value.which_optional_charge_limit_soc_min==CarServer_ChargeState_charge_limit_soc_min_tag)out.put("charge_limit_soc_min",value.optional_charge_limit_soc_min.charge_limit_soc_min);
 if(value.which_optional_charge_limit_soc_max==CarServer_ChargeState_charge_limit_soc_max_tag)out.put("charge_limit_soc_max",value.optional_charge_limit_soc_max.charge_limit_soc_max);
 if(value.which_optional_max_range_charge_counter==CarServer_ChargeState_max_range_charge_counter_tag)out.put("max_range_charge_counter",value.optional_max_range_charge_counter.max_range_charge_counter);
 if(value.which_optional_fast_charger_present==CarServer_ChargeState_fast_charger_present_tag)out.put("fast_charger_present",value.optional_fast_charger_present.fast_charger_present);
 if(value.which_optional_battery_range==CarServer_ChargeState_battery_range_tag)out.put("battery_range",value.optional_battery_range.battery_range);
 if(value.which_optional_est_battery_range==CarServer_ChargeState_est_battery_range_tag)out.put("est_battery_range",value.optional_est_battery_range.est_battery_range);
 if(value.which_optional_ideal_battery_range==CarServer_ChargeState_ideal_battery_range_tag)out.put("ideal_battery_range",value.optional_ideal_battery_range.ideal_battery_range);
 if(value.which_optional_battery_level==CarServer_ChargeState_battery_level_tag)out.put("battery_level",value.optional_battery_level.battery_level);
 if(value.which_optional_usable_battery_level==CarServer_ChargeState_usable_battery_level_tag)out.put("usable_battery_level",value.optional_usable_battery_level.usable_battery_level);
 if(value.which_optional_charge_energy_added==CarServer_ChargeState_charge_energy_added_tag)out.put("charge_energy_added",value.optional_charge_energy_added.charge_energy_added);
 if(value.which_optional_charge_miles_added_rated==CarServer_ChargeState_charge_miles_added_rated_tag)out.put("charge_miles_added_rated",value.optional_charge_miles_added_rated.charge_miles_added_rated);
 if(value.which_optional_charge_miles_added_ideal==CarServer_ChargeState_charge_miles_added_ideal_tag)out.put("charge_miles_added_ideal",value.optional_charge_miles_added_ideal.charge_miles_added_ideal);
 if(value.which_optional_charger_voltage==CarServer_ChargeState_charger_voltage_tag)out.put("charger_voltage",value.optional_charger_voltage.charger_voltage);
 if(value.which_optional_charger_pilot_current==CarServer_ChargeState_charger_pilot_current_tag)out.put("charger_pilot_current",value.optional_charger_pilot_current.charger_pilot_current);
 if(value.which_optional_charger_actual_current==CarServer_ChargeState_charger_actual_current_tag)out.put("charger_actual_current",value.optional_charger_actual_current.charger_actual_current);
 if(value.which_optional_charger_power==CarServer_ChargeState_charger_power_tag)out.put("charger_power",value.optional_charger_power.charger_power);
 if(value.which_optional_minutes_to_full_charge==CarServer_ChargeState_minutes_to_full_charge_tag)out.put("minutes_to_full_charge",value.optional_minutes_to_full_charge.minutes_to_full_charge);
 if(value.which_optional_trip_charging==CarServer_ChargeState_trip_charging_tag)out.put("trip_charging",value.optional_trip_charging.trip_charging);
 if(value.which_optional_charge_rate_mph==CarServer_ChargeState_charge_rate_mph_tag)out.put("charge_rate_mph",value.optional_charge_rate_mph.charge_rate_mph);
 if(value.which_optional_charge_port_door_open==CarServer_ChargeState_charge_port_door_open_tag)out.put("charge_port_door_open",value.optional_charge_port_door_open.charge_port_door_open);
 if(value.which_optional_scheduled_charging_start_time==CarServer_ChargeState_scheduled_charging_start_time_tag)out.put("scheduled_charging_start_time",value.optional_scheduled_charging_start_time.scheduled_charging_start_time);
 if(value.which_optional_scheduled_charging_pending==CarServer_ChargeState_scheduled_charging_pending_tag)out.put("scheduled_charging_pending",value.optional_scheduled_charging_pending.scheduled_charging_pending);
 if(value.which_optional_user_charge_enable_request==CarServer_ChargeState_user_charge_enable_request_tag)out.put("user_charge_enable_request",value.optional_user_charge_enable_request.user_charge_enable_request);
 if(value.which_optional_charge_enable_request==CarServer_ChargeState_charge_enable_request_tag)out.put("charge_enable_request",value.optional_charge_enable_request.charge_enable_request);
 if(value.which_optional_charger_phases==CarServer_ChargeState_charger_phases_tag)out.put("charger_phases",value.optional_charger_phases.charger_phases);
 if(value.which_optional_charge_port_cold_weather_mode==CarServer_ChargeState_charge_port_cold_weather_mode_tag)out.put("charge_port_cold_weather_mode",value.optional_charge_port_cold_weather_mode.charge_port_cold_weather_mode);
 if(value.which_optional_charge_current_request==CarServer_ChargeState_charge_current_request_tag)out.put("charge_current_request",value.optional_charge_current_request.charge_current_request);
 if(value.which_optional_charge_current_request_max==CarServer_ChargeState_charge_current_request_max_tag)out.put("charge_current_request_max",value.optional_charge_current_request_max.charge_current_request_max);
 if(value.which_optional_managed_charging_active==CarServer_ChargeState_managed_charging_active_tag)out.put("managed_charging_active",value.optional_managed_charging_active.managed_charging_active);
 if(value.which_optional_managed_charging_user_canceled==CarServer_ChargeState_managed_charging_user_canceled_tag)out.put("managed_charging_user_canceled",value.optional_managed_charging_user_canceled.managed_charging_user_canceled);
 if(value.which_optional_managed_charging_start_time==CarServer_ChargeState_managed_charging_start_time_tag)out.put("managed_charging_start_time",value.optional_managed_charging_start_time.managed_charging_start_time);
 if(value.which_optional_minutes_to_charge_limit==CarServer_ChargeState_minutes_to_charge_limit_tag)out.put("minutes_to_charge_limit",value.optional_minutes_to_charge_limit.minutes_to_charge_limit);
 if(value.which_optional_off_peak_hours_end_time==CarServer_ChargeState_off_peak_hours_end_time_tag)out.put("off_peak_hours_end_time",value.optional_off_peak_hours_end_time.off_peak_hours_end_time);
 if(value.which_optional_charging_amps==CarServer_ChargeState_charging_amps_tag)out.put("charging_amps",value.optional_charging_amps.charging_amps);
 if(value.which_optional_scheduled_charging_start_time_minutes==CarServer_ChargeState_scheduled_charging_start_time_minutes_tag)out.put("scheduled_charging_start_time_minutes",value.optional_scheduled_charging_start_time_minutes.scheduled_charging_start_time_minutes);
 if(value.which_optional_scheduled_departure_time_minutes==CarServer_ChargeState_scheduled_departure_time_minutes_tag)out.put("scheduled_departure_time_minutes",value.optional_scheduled_departure_time_minutes.scheduled_departure_time_minutes);
 if(value.which_optional_preconditioning_enabled==CarServer_ChargeState_preconditioning_enabled_tag)out.put("preconditioning_enabled",value.optional_preconditioning_enabled.preconditioning_enabled);
 if(value.which_optional_scheduled_charging_start_time_app==CarServer_ChargeState_scheduled_charging_start_time_app_tag)out.put("scheduled_charging_start_time_app",value.optional_scheduled_charging_start_time_app.scheduled_charging_start_time_app);
 if(value.which_optional_supercharger_session_trip_planner==CarServer_ChargeState_supercharger_session_trip_planner_tag)out.put("supercharger_session_trip_planner",value.optional_supercharger_session_trip_planner.supercharger_session_trip_planner);
 if(value.which_optional_charge_rate_mph_float==CarServer_ChargeState_charge_rate_mph_float_tag)out.put("charge_rate_mph_float",value.optional_charge_rate_mph_float.charge_rate_mph_float);
 if(value.which_optional_charge_cable_unlatched==CarServer_ChargeState_charge_cable_unlatched_tag)out.put("charge_cable_unlatched",value.optional_charge_cable_unlatched.charge_cable_unlatched);
 if(value.which_optional_powershare_feature_allowed==CarServer_ChargeState_powershare_feature_allowed_tag)out.put("powershare_feature_allowed",value.optional_powershare_feature_allowed.powershare_feature_allowed);
 if(value.which_optional_powershare_feature_enabled==CarServer_ChargeState_powershare_feature_enabled_tag)out.put("powershare_feature_enabled",value.optional_powershare_feature_enabled.powershare_feature_enabled);
 if(value.which_optional_powershare_request==CarServer_ChargeState_powershare_request_tag)out.put("powershare_request",value.optional_powershare_request.powershare_request);
 if(value.which_optional_powershare_instantaneous_load_kw==CarServer_ChargeState_powershare_instantaneous_load_kw_tag)out.put("powershare_instantaneous_load_kw",value.optional_powershare_instantaneous_load_kw.powershare_instantaneous_load_kw);
 if(value.which_optional_powershare_vehicle_energy_left_hr==CarServer_ChargeState_powershare_vehicle_energy_left_hr_tag)out.put("powershare_vehicle_energy_left_hr",value.optional_powershare_vehicle_energy_left_hr.powershare_vehicle_energy_left_hr);
 if(value.which_optional_powershare_soc_limit==CarServer_ChargeState_powershare_soc_limit_tag)out.put("powershare_soc_limit",value.optional_powershare_soc_limit.powershare_soc_limit);
 if(value.which_optional_one_time_soc_limit==CarServer_ChargeState_one_time_soc_limit_tag)out.put("one_time_soc_limit",value.optional_one_time_soc_limit.one_time_soc_limit);
 if(value.which_optional_outlet_max_timer_minutes==CarServer_ChargeState_outlet_max_timer_minutes_tag)out.put("outlet_max_timer_minutes",value.optional_outlet_max_timer_minutes.outlet_max_timer_minutes);
 if(value.has_charging_state&&value.charging_state.which_type!=0){
  std::string state=TeslaBLE::state_text::charging_state(value.charging_state.which_type);
  if(state=="No Power")state="NoPower";
  if(state!="Unknown")out.put("charging_state",state);
 }
 if(value.has_conn_charge_cable){switch(value.conn_charge_cable.which_type){
  case CarServer_ChargeState_CableType_SNA_tag:out.put("conn_charge_cable","SNA");break;
  case CarServer_ChargeState_CableType_IEC_tag:out.put("conn_charge_cable","IEC");break;
  case CarServer_ChargeState_CableType_SAE_tag:out.put("conn_charge_cable","SAE");break;
  case CarServer_ChargeState_CableType_GB_AC_tag:out.put("conn_charge_cable","GB_AC");break;
  case CarServer_ChargeState_CableType_GB_DC_tag:out.put("conn_charge_cable","GB_DC");break;
  default:break;
 }}
 if(value.has_charge_port_latch)out.put("charge_port_latch_tag",static_cast<int>(value.charge_port_latch.which_type));
 return out.finish();
}
inline std::string legacy_climate_json(const CarServer_ClimateState& value){
 LegacyStateJson out("climate");
 if(value.which_optional_inside_temp_celsius==CarServer_ClimateState_inside_temp_celsius_tag)out.put("inside_temp_celsius",value.optional_inside_temp_celsius.inside_temp_celsius);
 if(value.which_optional_outside_temp_celsius==CarServer_ClimateState_outside_temp_celsius_tag)out.put("outside_temp_celsius",value.optional_outside_temp_celsius.outside_temp_celsius);
 if(value.which_optional_driver_temp_setting==CarServer_ClimateState_driver_temp_setting_tag)out.put("driver_temp_setting",value.optional_driver_temp_setting.driver_temp_setting);
 if(value.which_optional_passenger_temp_setting==CarServer_ClimateState_passenger_temp_setting_tag)out.put("passenger_temp_setting",value.optional_passenger_temp_setting.passenger_temp_setting);
 if(value.which_optional_left_temp_direction==CarServer_ClimateState_left_temp_direction_tag)out.put("left_temp_direction",value.optional_left_temp_direction.left_temp_direction);
 if(value.which_optional_right_temp_direction==CarServer_ClimateState_right_temp_direction_tag)out.put("right_temp_direction",value.optional_right_temp_direction.right_temp_direction);
 if(value.which_optional_is_front_defroster_on==CarServer_ClimateState_is_front_defroster_on_tag)out.put("is_front_defroster_on",value.optional_is_front_defroster_on.is_front_defroster_on);
 if(value.which_optional_is_rear_defroster_on==CarServer_ClimateState_is_rear_defroster_on_tag)out.put("is_rear_defroster_on",value.optional_is_rear_defroster_on.is_rear_defroster_on);
 if(value.which_optional_fan_status==CarServer_ClimateState_fan_status_tag)out.put("fan_status",value.optional_fan_status.fan_status);
 if(value.which_optional_is_climate_on==CarServer_ClimateState_is_climate_on_tag)out.put("is_climate_on",value.optional_is_climate_on.is_climate_on);
 if(value.which_optional_min_avail_temp_celsius==CarServer_ClimateState_min_avail_temp_celsius_tag)out.put("min_avail_temp_celsius",value.optional_min_avail_temp_celsius.min_avail_temp_celsius);
 if(value.which_optional_max_avail_temp_celsius==CarServer_ClimateState_max_avail_temp_celsius_tag)out.put("max_avail_temp_celsius",value.optional_max_avail_temp_celsius.max_avail_temp_celsius);
 if(value.which_optional_seat_heater_left==CarServer_ClimateState_seat_heater_left_tag)out.put("seat_heater_left",value.optional_seat_heater_left.seat_heater_left);
 if(value.which_optional_seat_heater_right==CarServer_ClimateState_seat_heater_right_tag)out.put("seat_heater_right",value.optional_seat_heater_right.seat_heater_right);
 if(value.which_optional_seat_heater_rear_left==CarServer_ClimateState_seat_heater_rear_left_tag)out.put("seat_heater_rear_left",value.optional_seat_heater_rear_left.seat_heater_rear_left);
 if(value.which_optional_seat_heater_rear_right==CarServer_ClimateState_seat_heater_rear_right_tag)out.put("seat_heater_rear_right",value.optional_seat_heater_rear_right.seat_heater_rear_right);
 if(value.which_optional_seat_heater_rear_center==CarServer_ClimateState_seat_heater_rear_center_tag)out.put("seat_heater_rear_center",value.optional_seat_heater_rear_center.seat_heater_rear_center);
 if(value.which_optional_seat_heater_rear_right_back==CarServer_ClimateState_seat_heater_rear_right_back_tag)out.put("seat_heater_rear_right_back",value.optional_seat_heater_rear_right_back.seat_heater_rear_right_back);
 if(value.which_optional_seat_heater_rear_left_back==CarServer_ClimateState_seat_heater_rear_left_back_tag)out.put("seat_heater_rear_left_back",value.optional_seat_heater_rear_left_back.seat_heater_rear_left_back);
 if(value.which_optional_seat_heater_third_row_right==CarServer_ClimateState_seat_heater_third_row_right_tag)out.put("seat_heater_third_row_right",value.optional_seat_heater_third_row_right.seat_heater_third_row_right);
 if(value.which_optional_seat_heater_third_row_left==CarServer_ClimateState_seat_heater_third_row_left_tag)out.put("seat_heater_third_row_left",value.optional_seat_heater_third_row_left.seat_heater_third_row_left);
 if(value.which_optional_battery_heater==CarServer_ClimateState_battery_heater_tag)out.put("battery_heater",value.optional_battery_heater.battery_heater);
 if(value.which_optional_battery_heater_no_power==CarServer_ClimateState_battery_heater_no_power_tag)out.put("battery_heater_no_power",value.optional_battery_heater_no_power.battery_heater_no_power);
 if(value.which_optional_steering_wheel_heater==CarServer_ClimateState_steering_wheel_heater_tag)out.put("steering_wheel_heater",value.optional_steering_wheel_heater.steering_wheel_heater);
 if(value.which_optional_wiper_blade_heater==CarServer_ClimateState_wiper_blade_heater_tag)out.put("wiper_blade_heater",value.optional_wiper_blade_heater.wiper_blade_heater);
 if(value.which_optional_side_mirror_heaters==CarServer_ClimateState_side_mirror_heaters_tag)out.put("side_mirror_heaters",value.optional_side_mirror_heaters.side_mirror_heaters);
 if(value.which_optional_is_preconditioning==CarServer_ClimateState_is_preconditioning_tag)out.put("is_preconditioning",value.optional_is_preconditioning.is_preconditioning);
 if(value.which_optional_remote_heater_control_enabled==CarServer_ClimateState_remote_heater_control_enabled_tag)out.put("remote_heater_control_enabled",value.optional_remote_heater_control_enabled.remote_heater_control_enabled);
 if(value.which_optional_bioweapon_mode_on==CarServer_ClimateState_bioweapon_mode_on_tag)out.put("bioweapon_mode_on",value.optional_bioweapon_mode_on.bioweapon_mode_on);
 if(value.which_optional_is_auto_conditioning_on==CarServer_ClimateState_is_auto_conditioning_on_tag)out.put("is_auto_conditioning_on",value.optional_is_auto_conditioning_on.is_auto_conditioning_on);
 if(value.which_optional_auto_seat_climate_left==CarServer_ClimateState_auto_seat_climate_left_tag)out.put("auto_seat_climate_left",value.optional_auto_seat_climate_left.auto_seat_climate_left);
 if(value.which_optional_auto_seat_climate_right==CarServer_ClimateState_auto_seat_climate_right_tag)out.put("auto_seat_climate_right",value.optional_auto_seat_climate_right.auto_seat_climate_right);
 if(value.which_optional_seat_fan_front_left==CarServer_ClimateState_seat_fan_front_left_tag)out.put("seat_fan_front_left",value.optional_seat_fan_front_left.seat_fan_front_left);
 if(value.which_optional_seat_fan_front_right==CarServer_ClimateState_seat_fan_front_right_tag)out.put("seat_fan_front_right",value.optional_seat_fan_front_right.seat_fan_front_right);
 if(value.which_optional_allow_cabin_overheat_protection==CarServer_ClimateState_allow_cabin_overheat_protection_tag)out.put("allow_cabin_overheat_protection",value.optional_allow_cabin_overheat_protection.allow_cabin_overheat_protection);
 if(value.which_optional_supports_fan_only_cabin_overheat_protection==CarServer_ClimateState_supports_fan_only_cabin_overheat_protection_tag)out.put("supports_fan_only_cabin_overheat_protection",value.optional_supports_fan_only_cabin_overheat_protection.supports_fan_only_cabin_overheat_protection);
 if(value.which_optional_cabin_overheat_protection_actively_cooling==CarServer_ClimateState_cabin_overheat_protection_actively_cooling_tag)out.put("cabin_overheat_protection_actively_cooling",value.optional_cabin_overheat_protection_actively_cooling.cabin_overheat_protection_actively_cooling);
 if(value.which_optional_auto_steering_wheel_heat==CarServer_ClimateState_auto_steering_wheel_heat_tag)out.put("auto_steering_wheel_heat",value.optional_auto_steering_wheel_heat.auto_steering_wheel_heat);
 return out.finish();
}
inline std::string legacy_tyre_json(const CarServer_TirePressureState& value){
 LegacyStateJson out("tyre");
 if(value.which_optional_tpms_pressure_fl==CarServer_TirePressureState_tpms_pressure_fl_tag)out.put("tpms_pressure_fl",value.optional_tpms_pressure_fl.tpms_pressure_fl);
 if(value.which_optional_tpms_pressure_fr==CarServer_TirePressureState_tpms_pressure_fr_tag)out.put("tpms_pressure_fr",value.optional_tpms_pressure_fr.tpms_pressure_fr);
 if(value.which_optional_tpms_pressure_rl==CarServer_TirePressureState_tpms_pressure_rl_tag)out.put("tpms_pressure_rl",value.optional_tpms_pressure_rl.tpms_pressure_rl);
 if(value.which_optional_tpms_pressure_rr==CarServer_TirePressureState_tpms_pressure_rr_tag)out.put("tpms_pressure_rr",value.optional_tpms_pressure_rr.tpms_pressure_rr);
 if(value.which_optional_tpms_hard_warning_fl==CarServer_TirePressureState_tpms_hard_warning_fl_tag)out.put("tpms_hard_warning_fl",value.optional_tpms_hard_warning_fl.tpms_hard_warning_fl);
 if(value.which_optional_tpms_hard_warning_fr==CarServer_TirePressureState_tpms_hard_warning_fr_tag)out.put("tpms_hard_warning_fr",value.optional_tpms_hard_warning_fr.tpms_hard_warning_fr);
 if(value.which_optional_tpms_hard_warning_rl==CarServer_TirePressureState_tpms_hard_warning_rl_tag)out.put("tpms_hard_warning_rl",value.optional_tpms_hard_warning_rl.tpms_hard_warning_rl);
 if(value.which_optional_tpms_hard_warning_rr==CarServer_TirePressureState_tpms_hard_warning_rr_tag)out.put("tpms_hard_warning_rr",value.optional_tpms_hard_warning_rr.tpms_hard_warning_rr);
 if(value.which_optional_tpms_soft_warning_fl==CarServer_TirePressureState_tpms_soft_warning_fl_tag)out.put("tpms_soft_warning_fl",value.optional_tpms_soft_warning_fl.tpms_soft_warning_fl);
 if(value.which_optional_tpms_soft_warning_fr==CarServer_TirePressureState_tpms_soft_warning_fr_tag)out.put("tpms_soft_warning_fr",value.optional_tpms_soft_warning_fr.tpms_soft_warning_fr);
 if(value.which_optional_tpms_soft_warning_rl==CarServer_TirePressureState_tpms_soft_warning_rl_tag)out.put("tpms_soft_warning_rl",value.optional_tpms_soft_warning_rl.tpms_soft_warning_rl);
 if(value.which_optional_tpms_soft_warning_rr==CarServer_TirePressureState_tpms_soft_warning_rr_tag)out.put("tpms_soft_warning_rr",value.optional_tpms_soft_warning_rr.tpms_soft_warning_rr);
 if(value.which_optional_tpms_rcp_front_value==CarServer_TirePressureState_tpms_rcp_front_value_tag)out.put("tpms_rcp_front_value",value.optional_tpms_rcp_front_value.tpms_rcp_front_value);
 if(value.which_optional_tpms_rcp_rear_value==CarServer_TirePressureState_tpms_rcp_rear_value_tag)out.put("tpms_rcp_rear_value",value.optional_tpms_rcp_rear_value.tpms_rcp_rear_value);
 return out.finish();
}
inline std::string legacy_closure_json(const CarServer_ClosuresState& value){
 LegacyStateJson out("closure");
 if(value.which_optional_tonneau_percent_open==CarServer_ClosuresState_tonneau_percent_open_tag)out.put("tonneau_percent_open",value.optional_tonneau_percent_open.tonneau_percent_open);
 if(value.which_optional_tonneau_in_motion==CarServer_ClosuresState_tonneau_in_motion_tag)out.put("tonneau_in_motion",value.optional_tonneau_in_motion.tonneau_in_motion);
 if(value.which_optional_door_open_driver_front==CarServer_ClosuresState_door_open_driver_front_tag)out.put("door_open_driver_front",value.optional_door_open_driver_front.door_open_driver_front);
 if(value.which_optional_door_open_driver_rear==CarServer_ClosuresState_door_open_driver_rear_tag)out.put("door_open_driver_rear",value.optional_door_open_driver_rear.door_open_driver_rear);
 if(value.which_optional_door_open_passenger_front==CarServer_ClosuresState_door_open_passenger_front_tag)out.put("door_open_passenger_front",value.optional_door_open_passenger_front.door_open_passenger_front);
 if(value.which_optional_door_open_passenger_rear==CarServer_ClosuresState_door_open_passenger_rear_tag)out.put("door_open_passenger_rear",value.optional_door_open_passenger_rear.door_open_passenger_rear);
 if(value.which_optional_door_open_trunk_front==CarServer_ClosuresState_door_open_trunk_front_tag)out.put("door_open_trunk_front",value.optional_door_open_trunk_front.door_open_trunk_front);
 if(value.which_optional_door_open_trunk_rear==CarServer_ClosuresState_door_open_trunk_rear_tag)out.put("door_open_trunk_rear",value.optional_door_open_trunk_rear.door_open_trunk_rear);
 if(value.which_optional_window_open_driver_front==CarServer_ClosuresState_window_open_driver_front_tag)out.put("window_open_driver_front",value.optional_window_open_driver_front.window_open_driver_front);
 if(value.which_optional_window_open_passenger_front==CarServer_ClosuresState_window_open_passenger_front_tag)out.put("window_open_passenger_front",value.optional_window_open_passenger_front.window_open_passenger_front);
 if(value.which_optional_window_open_driver_rear==CarServer_ClosuresState_window_open_driver_rear_tag)out.put("window_open_driver_rear",value.optional_window_open_driver_rear.window_open_driver_rear);
 if(value.which_optional_window_open_passenger_rear==CarServer_ClosuresState_window_open_passenger_rear_tag)out.put("window_open_passenger_rear",value.optional_window_open_passenger_rear.window_open_passenger_rear);
 if(value.which_optional_sun_roof_percent_open==CarServer_ClosuresState_sun_roof_percent_open_tag)out.put("sun_roof_percent_open",value.optional_sun_roof_percent_open.sun_roof_percent_open);
 if(value.which_optional_locked==CarServer_ClosuresState_locked_tag)out.put("locked",value.optional_locked.locked);
 if(value.which_optional_is_user_present==CarServer_ClosuresState_is_user_present_tag)out.put("is_user_present",value.optional_is_user_present.is_user_present);
 if(value.which_optional_remote_start==CarServer_ClosuresState_remote_start_tag)out.put("remote_start",value.optional_remote_start.remote_start);
 if(value.which_optional_valet_mode==CarServer_ClosuresState_valet_mode_tag)out.put("valet_mode",value.optional_valet_mode.valet_mode);
 if(value.which_optional_valet_pin_needed==CarServer_ClosuresState_valet_pin_needed_tag)out.put("valet_pin_needed",value.optional_valet_pin_needed.valet_pin_needed);
 if(value.which_optional_sentry_mode_available==CarServer_ClosuresState_sentry_mode_available_tag)out.put("sentry_mode_available",value.optional_sentry_mode_available.sentry_mode_available);
 if(value.has_sentry_mode_state){
  const int state=value.sentry_mode_state.which_type;
  if(state==CarServer_ClosuresState_SentryModeState_Off_tag)out.put("sentry_mode",false);
  else if(TeslaBLE::state_text::sentry_mode_active(state))out.put("sentry_mode",true);
 }
 return out.finish();
}
inline std::string legacy_drive_json(const CarServer_DriveState& value){
 LegacyStateJson out("drive");
 if(value.which_optional_active_route_minutes_to_arrival==CarServer_DriveState_active_route_minutes_to_arrival_tag)out.put("active_route_minutes_to_arrival",value.optional_active_route_minutes_to_arrival.active_route_minutes_to_arrival);
 if(value.which_optional_active_route_miles_to_arrival==CarServer_DriveState_active_route_miles_to_arrival_tag)out.put("active_route_miles_to_arrival",value.optional_active_route_miles_to_arrival.active_route_miles_to_arrival);
 if(value.which_optional_active_route_traffic_minutes_delay==CarServer_DriveState_active_route_traffic_minutes_delay_tag)out.put("active_route_traffic_minutes_delay",value.optional_active_route_traffic_minutes_delay.active_route_traffic_minutes_delay);
 if(value.which_optional_active_route_energy_at_arrival==CarServer_DriveState_active_route_energy_at_arrival_tag)out.put("active_route_energy_at_arrival",value.optional_active_route_energy_at_arrival.active_route_energy_at_arrival);
 if(value.which_optional_last_route_update==CarServer_DriveState_last_route_update_tag)out.put("last_route_update",value.optional_last_route_update.last_route_update);
 if(value.which_optional_speed==CarServer_DriveState_speed_tag)out.put("speed",value.optional_speed.speed);
 if(value.which_optional_power==CarServer_DriveState_power_tag)out.put("power",value.optional_power.power);
 if(value.which_optional_odometer_in_hundredths_of_a_mile==CarServer_DriveState_odometer_in_hundredths_of_a_mile_tag)out.put("odometer_in_hundredths_of_a_mile",value.optional_odometer_in_hundredths_of_a_mile.odometer_in_hundredths_of_a_mile);
 if(value.which_optional_speed_float==CarServer_DriveState_speed_float_tag)out.put("speed_float",value.optional_speed_float.speed_float);
 if(value.has_shift_state&&TeslaBLE::state_text::shift_state(value.shift_state.which_type)!="Unknown")out.put("shift_state",TeslaBLE::state_text::shift_state(value.shift_state.which_type));
 return out.finish();
}
inline std::string legacy_media_json(const CarServer_MediaState& value){
 LegacyStateJson out("media");
 if(value.which_optional_remote_control_enabled==CarServer_MediaState_remote_control_enabled_tag)out.put("remote_control_enabled",value.optional_remote_control_enabled.remote_control_enabled);
 if(value.which_optional_audio_volume==CarServer_MediaState_audio_volume_tag)out.put("audio_volume",value.optional_audio_volume.audio_volume);
 if(value.which_optional_audio_volume_increment==CarServer_MediaState_audio_volume_increment_tag)out.put("audio_volume_increment",value.optional_audio_volume_increment.audio_volume_increment);
 if(value.which_optional_audio_volume_max==CarServer_MediaState_audio_volume_max_tag)out.put("audio_volume_max",value.optional_audio_volume_max.audio_volume_max);
 return out.finish();
}
inline std::string legacy_software_json(const CarServer_SoftwareUpdateState& value){
 LegacyStateJson out("software");
 if(value.which_optional_scheduled_time_ms==CarServer_SoftwareUpdateState_scheduled_time_ms_tag)out.put("scheduled_time_ms",value.optional_scheduled_time_ms.scheduled_time_ms);
 if(value.which_optional_warning_time_remaining_ms==CarServer_SoftwareUpdateState_warning_time_remaining_ms_tag)out.put("warning_time_remaining_ms",value.optional_warning_time_remaining_ms.warning_time_remaining_ms);
 if(value.which_optional_expected_duration_sec==CarServer_SoftwareUpdateState_expected_duration_sec_tag)out.put("expected_duration_sec",value.optional_expected_duration_sec.expected_duration_sec);
 if(value.which_optional_download_perc==CarServer_SoftwareUpdateState_download_perc_tag)out.put("download_perc",value.optional_download_perc.download_perc);
 if(value.which_optional_install_perc==CarServer_SoftwareUpdateState_install_perc_tag)out.put("install_perc",value.optional_install_perc.install_perc);
 return out.finish();
}
inline std::string legacy_vcsec_json(const VCSEC_VehicleStatus& value){
 LegacyStateJson out("vcsec");
 auto sleep=TeslaBLE::state_text::sleep_status(value.vehicleSleepStatus);
 if(sleep)out.put("asleep",*sleep);
 auto unlocked=TeslaBLE::state_text::lock_status(value.vehicleLockState);
 if(unlocked)out.put("locked",!*unlocked);
 auto present=TeslaBLE::state_text::user_presence(value.userPresence);
 if(present)out.put("user_present",*present);
 if(value.has_closureStatuses){
  {const auto state=value.closureStatuses.frontDriverDoor;if(TeslaBLE::state_text::closure_state_text(state)!="unknown")out.put("frontDriverDoor",TeslaBLE::state_text::closure_state_text(state));}
  {const auto state=value.closureStatuses.frontPassengerDoor;if(TeslaBLE::state_text::closure_state_text(state)!="unknown")out.put("frontPassengerDoor",TeslaBLE::state_text::closure_state_text(state));}
  {const auto state=value.closureStatuses.rearDriverDoor;if(TeslaBLE::state_text::closure_state_text(state)!="unknown")out.put("rearDriverDoor",TeslaBLE::state_text::closure_state_text(state));}
  {const auto state=value.closureStatuses.rearPassengerDoor;if(TeslaBLE::state_text::closure_state_text(state)!="unknown")out.put("rearPassengerDoor",TeslaBLE::state_text::closure_state_text(state));}
  {const auto state=value.closureStatuses.rearTrunk;if(TeslaBLE::state_text::closure_state_text(state)!="unknown")out.put("rearTrunk",TeslaBLE::state_text::closure_state_text(state));}
  {const auto state=value.closureStatuses.frontTrunk;if(TeslaBLE::state_text::closure_state_text(state)!="unknown")out.put("frontTrunk",TeslaBLE::state_text::closure_state_text(state));}
  {const auto state=value.closureStatuses.chargePort;if(TeslaBLE::state_text::closure_state_text(state)!="unknown")out.put("chargePort",TeslaBLE::state_text::closure_state_text(state));}
  {const auto state=value.closureStatuses.tonneau;if(TeslaBLE::state_text::closure_state_text(state)!="unknown")out.put("tonneau",TeslaBLE::state_text::closure_state_text(state));}
 }
 return out.finish();
}
}
