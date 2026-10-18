package org.opencharge.teslable;
import java.util.*;
public class LegacyMqttStateMapperHarness {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  Map<String,String> m=LegacyMqttStateMapper.map("charge",Map.of("battery_level","0","battery_range","100","charger_power","0","charge_enable_request","false","charging_state","Stopped","conn_charge_cable","SAE"));
  check(m.get("sensor/charge_state").equals("0"),"zero battery");check(m.get("sensor/battery_range").equals("100"),"legacy range remains miles");check(m.get("sensor/charger_power").equals("0"),"zero power");check(m.get("switch/charge_enable_request").equals("false"),"false preserved");check(m.get("sensor/charging_state").equals("Stopped"),"charging enum");check(m.get("sensor/charge_cable").equals("SAE"),"cable enum");
  check(!m.containsKey("sensor/charger_voltage"),"missing voltage not invented");
  m=LegacyMqttStateMapper.map("climate",Map.of("seat_heater_left","2","is_climate_on","false","outside_temp_celsius","-3.5"));
  check(m.get("select/seat_heater_left").equals("med"),"seat enum med");check(m.get("switch/is_climate_on").equals("false"),"climate false");check(m.get("sensor/outside_temp").equals("-3.5"),"Celsius unchanged");
  m=LegacyMqttStateMapper.map("drive",Map.of("odometer_in_hundredths_of_a_mile","1234567"));check(m.get("sensor/odometer").equals("12345"),"legacy whole miles truncation");
  m=LegacyMqttStateMapper.map("closure",Map.of("window_open_driver_front","false"));check(!m.containsKey("cover/windows"),"partial windows cannot imply closed");
  m=LegacyMqttStateMapper.map("closure",Map.of("window_open_driver_front","true"));check(m.get("cover/windows").equals("true"),"any positive window wins");
  m=LegacyMqttStateMapper.map("closure",Map.of("window_open_driver_front","false","window_open_passenger_front","false","window_open_driver_rear","false","window_open_passenger_rear","false"));check(m.get("cover/windows").equals("false"),"all four known closed");
  m=LegacyMqttStateMapper.map("vcsec",Map.of("asleep","true","locked","false","user_present","false"));check(m.get("binary_sensor/awake").equals("false"),"sleep inversion");check(m.get("binary_sensor/door_lock").equals("false"),"lock false");check(!m.containsKey("binary_sensor/presence"),"driver absence is not vehicle absence");
  for(String invalid:new String[]{"NaN","Infinity","-1","9999","20;lock"})check(LegacyMqttStateMapper.map("charge",Map.of("battery_level",invalid)).isEmpty(),"invalid battery rejected");
  check(LegacyMqttStateMapper.map("unknown",Map.of("battery_level","20")).isEmpty(),"unknown section ignored");
  System.out.println("PASS legacy field mapping, units, enums, zero/false and missing values");
 }
}
