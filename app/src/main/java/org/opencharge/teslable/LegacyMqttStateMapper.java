package org.opencharge.teslable;
import java.util.*;
import java.math.BigDecimal;
import java.util.regex.Pattern;
/** Exact legacy state paths; authenticated observation time/freshness is enforced by the owner. */
public final class LegacyMqttStateMapper {
 private LegacyMqttStateMapper() {}
 private static final Pattern NUMBER=Pattern.compile("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]{1,3})?");
 private static final String[][] FIELDS={
  {"charge","battery_level","sensor/charge_state","number"},
  {"charge","battery_range","sensor/battery_range","number"},
  {"charge","charger_power","sensor/charger_power","number"},
  {"charge","charger_actual_current","sensor/charger_actual_current","number"},
  {"charge","charger_voltage","sensor/charger_voltage","number"},
  {"charge","charge_energy_added","sensor/charge_energy_added","number"},
  {"charge","charge_miles_added_rated","sensor/charge_range_added","number"},
  {"charge","charge_rate_mph","sensor/charge_speed","number"},
  {"charge","conn_charge_cable","sensor/charge_cable","cable"},
  {"charge","charging_state","sensor/charging_state","charging"},
  {"charge","charge_enable_request","switch/charge_enable_request","boolean"},
  {"charge","charge_port_door_open","cover/charge_port_door_open","boolean"},
  {"charge","charge_current_request","number/charge_current_request","number"},
  {"charge","charge_limit_soc","number/charge_limit_soc","number"},
  {"climate","inside_temp_celsius","sensor/inside_temp","number"},
  {"climate","outside_temp_celsius","sensor/outside_temp","number"},
  {"climate","passenger_temp_setting","sensor/passenger_temp","number"},
  {"climate","driver_temp_setting","number/driver_temp_setting","number"},
  {"climate","is_climate_on","switch/is_climate_on","boolean"},
  {"climate","steering_wheel_heater","switch/steering_wheel_heater","boolean"},
  {"climate","battery_heater","binary_sensor/battery_heater_on","boolean"},
  {"climate","is_front_defroster_on","binary_sensor/front_defrost","boolean"},
  {"climate","is_rear_defroster_on","binary_sensor/rear_defrost","boolean"},
  {"climate","wiper_blade_heater","binary_sensor/wiper_heater","boolean"},
  {"climate","side_mirror_heaters","binary_sensor/mirror_heater","boolean"},
  {"climate","seat_heater_left","select/seat_heater_left","seat"},
  {"climate","seat_heater_right","select/seat_heater_right","seat"},
  {"climate","seat_heater_rear_left","select/seat_heater_rear_left","seat"},
  {"climate","seat_heater_rear_right","select/seat_heater_rear_right","seat"},
  {"tyre","tpms_pressure_fl","sensor/tpms_pressure_fl","number"},
  {"tyre","tpms_pressure_fr","sensor/tpms_pressure_fr","number"},
  {"tyre","tpms_pressure_rl","sensor/tpms_pressure_rl","number"},
  {"tyre","tpms_pressure_rr","sensor/tpms_pressure_rr","number"},
  {"closure","sentry_mode","switch/sentry_mode","boolean"},
  {"closure","door_open_trunk_rear","cover/rear_trunk","boolean"},
  {"closure","door_open_trunk_front","binary_sensor/frunk_open","boolean"},
  {"closure","window_open_driver_front","binary_sensor/window_open_driver_front","boolean"},
  {"closure","window_open_passenger_front","binary_sensor/window_open_pass_front","boolean"},
  {"closure","window_open_driver_rear","binary_sensor/window_open_driver_rear","boolean"},
  {"closure","window_open_passenger_rear","binary_sensor/window_open_pass_rear","boolean"},
  {"closure","door_open_driver_front","binary_sensor/door_open_driver_front","boolean"},
  {"closure","door_open_passenger_front","binary_sensor/door_open_pass_front","boolean"},
  {"closure","door_open_driver_rear","binary_sensor/door_open_driver_rear","boolean"},
  {"closure","door_open_passenger_rear","binary_sensor/door_open_pass_rear","boolean"},
  {"closure","locked","binary_sensor/door_lock","boolean"},
  {"drive","odometer_in_hundredths_of_a_mile","sensor/odometer","odometer"},
 };
 public static Set<String> topicsForSection(String section) {
  Set<String> out=new LinkedHashSet<>();for(String[] f:FIELDS)if(f[0].equals(section))out.add(f[2]);
  if("closure".equals(section))out.add("cover/windows");
  if("vcsec".equals(section)){out.add("binary_sensor/awake");out.add("binary_sensor/door_lock");}
  return Collections.unmodifiableSet(out);
 }
 public static Map<String,String> map(String section,Map<String,String> observed) {
  Map<String,String> out=new LinkedHashMap<>();if(section==null||observed==null)return Collections.emptyMap();
  for(String[] f:FIELDS){if(!f[0].equals(section))continue;String value=observed.get(f[1]);if(value==null)continue;
   String result=normalize(f[1],f[3],value);if(result!=null)out.put(f[2],result);
  }
  if("closure".equals(section)){
   String[] windows={"window_open_driver_front","window_open_passenger_front","window_open_driver_rear","window_open_passenger_rear"};boolean allClosed=true,anyOpen=false;
   for(String window:windows){String value=observed.get(window);if("true".equals(value))anyOpen=true;if(!"false".equals(value))allClosed=false;}
   if(anyOpen)out.put("cover/windows","true");else if(allClosed)out.put("cover/windows","false");
  }
  if("vcsec".equals(section)){
   String asleep=observed.get("asleep");if("true".equals(asleep)||"false".equals(asleep))out.put("binary_sensor/awake",Boolean.toString(!Boolean.parseBoolean(asleep)));
   String locked=observed.get("locked");if("true".equals(locked)||"false".equals(locked))out.put("binary_sensor/door_lock",locked);
   // user_present is driver occupancy, never physical vehicle proximity.
  }
  return Collections.unmodifiableMap(out);
 }
 private static String normalize(String field,String kind,String value){
  if(value.length()>64)return null;
  if(kind.equals("boolean"))return value.equals("true")||value.equals("false")?value:null;
  if(kind.equals("seat")){switch(value){case "0":return "off";case "1":return "low";case "2":return "med";case "3":return "high";default:return null;}}
  if(kind.equals("charging"))return Set.of("Disconnected","NoPower","Starting","Charging","Complete","Stopped","Calibrating").contains(value)?value:null;
  if(kind.equals("cable"))return Set.of("SNA","IEC","SAE","GB_AC","GB_DC","No").contains(value)?value:null;
  if(!NUMBER.matcher(value).matches())return null;
  try {
   double number=Double.parseDouble(value);if(!Double.isFinite(number))return null;
   double minimum=field.contains("temp")?-100:0,maximum=field.contains("temp")?100:1e12;
   if(field.equals("battery_level")||field.equals("charge_limit_soc"))maximum=100;
   if(field.startsWith("tpms_pressure"))maximum=20;
   if(number<minimum||number>maximum)return null;
   BigDecimal decimal=new BigDecimal(value);
   if(kind.equals("odometer"))return decimal.divideToIntegralValue(BigDecimal.valueOf(100)).toBigInteger().toString();
   return decimal.stripTrailingZeros().toPlainString();
  }catch(NumberFormatException invalid){return null;}
 }
}
