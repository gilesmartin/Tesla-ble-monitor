package org.opencharge.teslable;
import java.util.*;
public final class LegacyMqttCommandHarness {
 public static void main(String[] a) {
  String vin="TESTTESTTESTTESTT";
  LegacyMqttCommand.Result c=LegacyMqttCommand.parse("tesla_ble/"+vin+"/command","lock",vin,false);
  if(!c.accepted() || !c.operation().equals("vehicle_command") || !c.arguments().get("command").equals("lock")) throw new AssertionError("lock canonical dispatch");
  for(String[] input:new String[][]{{"tesla_ble/"+vin+"/command","not-a-command",vin},{"tesla_ble/OTHER/command","lock",vin},{"tesla_ble/"+vin+"/variables/polling","lock",vin}}) if(LegacyMqttCommand.parse(input[0],input[1],input[2],false).accepted()) throw new AssertionError("malformed/wrong VIN accepted");
  LegacyMqttCommand.Result polling=LegacyMqttCommand.parse("tesla_ble/"+vin+"/variables/polling","on",vin,true);
  if(!polling.accepted() || !polling.operation().equals("set_polling") || !polling.arguments().get("value").equals("on")) throw new AssertionError("retained idempotent config");
  LegacyMqttCommand.Result keys=LegacyMqttCommand.parse("tesla_ble/"+vin+"/config","deploy-key",vin,false);
  if(!keys.accepted() || keys.confirmationNeeded()) throw new AssertionError("key operations are explicit opt-in MQTT commands, not deployment hooks");
  if(LegacyMqttCommand.parse("tesla_ble/"+vin+"/config","generate-keys",vin,true).accepted()) throw new AssertionError("retained key rotation accepted");
  String[] commands={"autosecure-modelx","charging-schedule-cancel","drive","flash-lights","frunk-open","honk","list-keys","lock","media-toggle-playback","ping","software-update-cancel","software-update-start","unlock","wake","body-controller-state","generate-keys","info-bt-adapter","read-state-all","read-state-envcheck","read-state-charge","read-state-climate","read-state-tyre","read-state-closure","read-state-drive","auto-seat-and-climate"};
  for(String cmd:commands){String topic=(cmd.startsWith("read-state")||cmd.equals("generate-keys")||cmd.equals("deploy-key")||cmd.equals("info-bt-adapter")||cmd.equals("body-controller-state"))?"config":(cmd.equals("auto-seat-and-climate")?cmd:"command"); if(!LegacyMqttCommand.parse("tesla_ble/"+vin+"/"+topic,cmd.equals("auto-seat-and-climate")?"on":cmd,vin,false).accepted()) throw new AssertionError("missed command "+cmd);}
  String root="tesla_ble/"+vin+"/";
  String[][] accepted={ {"charging","start"},{"charging","stop"},{"auto-seat-and-climate","auto-seat-and-climate"},{"heater-seat-front-left","med"},{"heater-seat-front-right","med"},{"heater-seat-rear-left","med"},{"heater-seat-rear-right","med"},{"media-set-volume","10.0"},{"charging-set-amps","0"},{"charging-set-limit","50"},{"climate-set-temp","28"},{"variables/polling_interval","3600"},{"poll_state","86400"} };
  for(String[] x:accepted) if(!LegacyMqttCommand.parse(root+x[0],x[1],vin,false).accepted()) throw new AssertionError("rejected upstream boundary "+Arrays.toString(x));
  for(String[] x:new String[][]{{"heater-seat-random","med"},{"heater-seat-front-left","medium"},{"media-set-volume","101"},{"media-set-volume","NaN"},{"media-set-volume","Infinity"},{"charging","on"},{"variables/polling_interval","3601"},{"poll_state","86401"},{"config","lock"},{"command","generate-keys"}}) if(LegacyMqttCommand.parse(root+x[0],x[1],vin,false).accepted()) throw new AssertionError("accepted invalid grammar "+Arrays.toString(x));
  if(!LegacyMqttCommand.parse(root+"command","auto-seat-and-climate",vin,false).accepted()) throw new AssertionError("discovered auto-seat command");
  for(String payload:new String[]{"999","-1","1;rm -rf /","on\nlock"}) if(LegacyMqttCommand.parse("tesla_ble/"+vin+"/charging-set-limit",payload,vin,false).accepted()) throw new AssertionError("bad numeric accepted "+payload);
  if(LegacyMqttCommand.parse("tesla_ble/"+vin+"/poll_state","-5",vin,false).accepted()) throw new AssertionError("bad poll count");
  if(LegacyMqttCommand.parse("tesla_ble/"+vin+"/command","unlock",vin,true).accepted()) throw new AssertionError("retained actuator accepted");
  if(LegacyMqttCommand.parse("tesla_ble/"+vin+"/command","lock\u0000",vin,false).accepted()) throw new AssertionError("control char accepted");
 }
}
