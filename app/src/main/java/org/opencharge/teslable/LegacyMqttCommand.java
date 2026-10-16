package org.opencharge.teslable;

import java.util.*;
import java.util.regex.Pattern;

/** Fail-closed parser for the legacy tesla_ble/<VIN>/<operation> MQTT grammar. */
public final class LegacyMqttCommand {
 private static final int MAX=256;
 private static final Pattern VIN=Pattern.compile("[A-HJ-NPR-Z0-9]{17}");
 private static final Set<String> COMMANDS=Set.of("auto-seat-and-climate","autosecure-modelx","charging-schedule-cancel","drive","flash-lights","frunk-open","honk","list-keys","lock","media-toggle-playback","ping","software-update-cancel","software-update-start","unlock","wake");
 private static final Set<String> READS=Set.of("body-controller-state","info-bt-adapter","read-state-all","read-state-envcheck","read-state-charge","read-state-climate","read-state-tyre","read-state-closure","read-state-drive");
 private LegacyMqttCommand(){}
 public static final class Result {
  private final boolean accepted,confirm; private final String operation,error; private final Map<String,String> args;
  private Result(boolean ok,String op,Map<String,String> a,boolean c,String e){accepted=ok;operation=op;args=Collections.unmodifiableMap(new LinkedHashMap<>(a));confirm=c;error=e;}
  public boolean accepted(){return accepted;} public String operation(){return operation;} public Map<String,String> arguments(){return args;} public boolean confirmationNeeded(){return confirm;} public String error(){return error;}
 }
 private static Result ok(String op,Map<String,String> a,boolean confirm){return new Result(true,op,a,confirm,null);}
 private static Result no(String why){return new Result(false,"",Map.of(),false,why);}
 private static Map<String,String> args(String... xs){Map<String,String> m=new LinkedHashMap<>();for(int i=0;i<xs.length;i+=2)m.put(xs[i],xs[i+1]);return m;}
 public static Result parse(String topic,String payload,String expectedVin,boolean retained){
  if(topic==null||payload==null||expectedVin==null||topic.length()>512||payload.length()>MAX)return no("missing or oversized input");
  for(int i=0;i<payload.length();i++)if(Character.isISOControl(payload.charAt(i)))return no("control character");
  if(!VIN.matcher(expectedVin).matches())return no("invalid expected VIN");
  String[] p=topic.split("/",-1); if(p.length<3||!p[0].equals("tesla_ble")||!p[1].equals(expectedVin)||p[1].length()!=17)return no("wrong topic or VIN");
  String key=String.join("/",Arrays.copyOfRange(p,2,p.length));
  if(retained&&!key.equals("variables/polling")&&!key.equals("variables/polling_interval"))return no("retained commands rejected");
  if(key.equals("variables/polling")){if(!Set.of("on","off").contains(payload))return no("invalid polling value");return ok("set_polling",args("value",payload),false);}
  if(key.equals("variables/polling_interval")){Integer n=integer(payload,30,3600);if(n==null)return no("invalid polling interval");return ok("set_polling_interval",args("seconds",n.toString()),false);}
  if(key.equals("poll_state")){Integer n=integer(payload,0,86400);if(n==null)return no("invalid poll count");return ok("poll_state",args("count",n.toString()),false);}
  if(key.equals("config")){
   if(READS.contains(payload))return ok("read_vehicle_state",args("command",payload),false);
   if(payload.equals("generate-keys")||payload.equals("deploy-key"))return ok("key_management",args("action",payload),false);
   return no("unknown config command");
  }
  if(key.equals("command")){if(COMMANDS.contains(payload))return ok("vehicle_command",args("command",payload),false);return no("unknown command");}
  if(key.equals("auto-seat-and-climate")){if(!Set.of("on","off","auto-seat-and-climate").contains(payload))return no("invalid boolean");return ok("auto_seat_climate",args("value",payload.equals("off")?"off":"on"),false);}
  if(Set.of("climate","sentry-mode","steering-wheel-heater").contains(key)){if(!Set.of("on","off").contains(payload))return no("invalid boolean");return ok("set_"+key,args("value",payload),false);}
  if(key.equals("charging")){if(!Set.of("start","stop").contains(payload))return no("invalid charging value");return ok("set_charging",args("value",payload),false);}
  if(Set.of("charge-port","trunk","windows","tonneau").contains(key)){if(!Set.of("open","close").contains(payload))return no("invalid position");return ok("set_"+key,args("value",payload),false);}
  if(key.equals("charging-set-limit")){Integer n=integer(payload,50,100);return n==null?no("charge limit outside 50..100"):ok("set_charge_limit",args("percent",n.toString()),false);}
  if(key.equals("charging-set-amps")||key.equals("charging-set-amps-override")){Integer n=integer(payload,0,32);return n==null?no("amps outside target vehicle range 0..32"):ok("set_charging_amps",args("amps",n.toString(),"override",Boolean.toString(key.endsWith("override"))),false);}
  if(key.equals("climate-set-temp")){try{double n=Double.parseDouble(payload);if(!decimal(payload)||!Double.isFinite(n)||n<15||n>28)return no("temperature outside 15..28 C");return ok("set_climate_temperature",args("celsius",String.format(Locale.ROOT,"%.2f",n)),false);}catch(Exception e){return no("invalid temperature");}}
  if(key.equals("charging-schedule")){Integer n=integer(payload,0,1439);return n==null?no("minutes after midnight outside 0..1439"):ok("set_charging_schedule",args("minutes_after_midnight",n.toString()),false);}
  if(key.equals("media-set-volume")){try{if(!decimal(payload))return no("invalid volume");double n=Double.parseDouble(payload);return !Double.isFinite(n)||n<0||n>10?no("volume outside 0..10"):ok("set_media_volume",args("level",String.format(Locale.ROOT,"%.2f",n)),false);}catch(NumberFormatException e){return no("invalid volume");}}
  if(Set.of("heater-seat-front-left","heater-seat-front-right","heater-seat-rear-left","heater-seat-rear-right").contains(key)){if(!Set.of("off","low","med","high").contains(payload))return no("invalid seat heat level");return ok("set_seat_heater",args("seat",key.substring(12),"level",payload),false);}
  if(key.equals("variables"))return no("variables root is not a command");
  return no("unknown legacy topic");
 }
 private static boolean decimal(String value){return value.matches("[0-9]{1,3}(?:\\.[0-9]{1,4})?");}
 private static Integer integer(String s,int min,int max){try{if(!s.matches("[0-9]{1,5}"))return null;int n=Integer.parseInt(s);return n>=min&&n<=max?n:null;}catch(Exception e){return null;}}
}
