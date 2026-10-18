package org.opencharge.teslable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in legacy runtime; native access is exclusively through its service-owned Owner. */
public final class LegacyMqttPublisher {
 public interface Owner {
  void post(Runnable work);
  long now();
  long freshnessMillis();
  boolean isReady();
  boolean isNativeInitialized();
  boolean hasExistingKey();
  boolean controlsEnabled();
  void onKeyRegenerated();
  boolean execute(String id,String operation,String[] args);
  boolean read(String section);
  boolean setPolicy(Boolean polling,Integer intervalSeconds);
  void diagnostic(String action);
  void abortTimedOutCommand();
 }
 private static final List<String> COMMAND_TOPICS=List.of("command","config","auto-seat-and-climate","climate","charging","sentry-mode","steering-wheel-heater","charge-port","trunk","windows","tonneau","charging-set-limit","charging-set-amps","charging-set-amps-override","climate-set-temp","charging-schedule","media-set-volume","heater-seat-front-left","heater-seat-front-right","heater-seat-rear-left","heater-seat-rear-right","variables/polling","variables/polling_interval","poll_state");
 private final String vin,root,broker,username,password;
 private final MqttBridgePublisher.Transport transport;
 private final Owner owner;
 private final List<LegacyMqttDiscoveryCatalog.Definition> definitions;
 private final Map<String,Map<String,String>> states=new LinkedHashMap<>();
 private final Map<String,Long> observations=new HashMap<>();
 private final Map<String,String> topicValues=new LinkedHashMap<>(),topicSources=new HashMap<>();
 private final Map<String,String> availability=new HashMap<>();
 private final AtomicInteger incoming=new AtomicInteger();
 private volatile boolean closed;
 private boolean started,connected,deliveryError;
 private String pendingId,pendingOperation,lastResult;
 private long deadline;
 public LegacyMqttPublisher(String broker,boolean allowPlaintext,String username,String password,String vin,MqttBridgePublisher.Transport transport,Owner owner){
  MqttBridgeConfig.validateBrokerUri(broker,allowPlaintext);MqttBridgeConfig.validateCredentials(username,password);
  if(transport==null||owner==null)throw new IllegalArgumentException("MQTT owner and transport required");
  this.definitions=LegacyMqttDiscoveryCatalog.create(vin);this.vin=vin;this.root="tesla_ble/"+vin;
  this.broker=broker;this.username=username;this.password=password;this.transport=transport;this.owner=owner;
 }
 public void start(){
  if(started||closed)return;started=true;
  transport.connect(broker,"tbl_android_"+vin,username,password.toCharArray(),root+"/availability","offline",new MqttBridgePublisher.Transport.Listener(){
   public void onConnected(){owner.post(()->connected());}
   public void onConnectionLost(){owner.post(()->{connected=false;availability.clear();});}
   public void onMessage(String topic,String payload){onMessage(topic,payload,false,false);}
   public void onMessage(String topic,String payload,boolean retained,boolean duplicate){
    if(closed)return;if(incoming.incrementAndGet()>32){incoming.decrementAndGet();return;}
    owner.post(()->{try{message(topic,payload,retained,duplicate);}finally{incoming.decrementAndGet();}});
   }
  });
 }
 private void connected(){
  if(closed)return;connected=true;availability.clear();publish(root+"/availability","offline",true);
  subscribe("homeassistant/status");for(String topic:COMMAND_TOPICS)subscribe(root+"/"+topic);
  discovery();replayFreshStates();refreshAvailability();publish(root+"/availability","online",true);
  if(lastResult!=null)publish(root+"/command_result",lastResult,false);
 }
 public boolean isConnected(){return connected&&!closed;}
 public boolean hasPendingCommand(){return pendingId!=null;}
 public boolean hasDeliveryError(){return deliveryError;}
 private void discovery(){for(LegacyMqttDiscoveryCatalog.Definition d:definitions)publish(d.getDiscoveryTopic(),d.toJson(),true);}
 private void message(String topic,String payload,boolean retained,boolean duplicate){
  if(closed||!connected)return;
  if("homeassistant/status".equals(topic)){
   if("online".equalsIgnoreCase(payload==null?"":payload.trim())){publish(root+"/availability","offline",true);discovery();replayFreshStates();availability.clear();refreshAvailability();publish(root+"/availability","online",true);}
   return;
  }
  if(topic==null||!topic.startsWith(root+"/")||!COMMAND_TOPICS.contains(topic.substring(root.length()+1)))return;
  LegacyMqttCommand.Result parsed=LegacyMqttCommand.parse(topic,payload,vin,retained);
  if(!parsed.accepted()){result("","rejected",false);return;}
  String operation=parsed.operation();Map<String,String> args=parsed.arguments();
  if(operation.equals("set_polling")||operation.equals("set_polling_interval")){
   boolean saved=operation.equals("set_polling")?owner.setPolicy("on".equals(args.get("value")),null):owner.setPolicy(null,Integer.valueOf(args.get("seconds")));
   result("",saved?"configuration_saved":"configuration_failed",false);refreshAvailability();return;
  }
  if(operation.equals("poll_state"))return; // Legacy diagnostic counter; never an actuation.
  if(parsed.confirmationNeeded()){result("","requires_local_confirmation",false);owner.diagnostic("key_management_requires_local_confirmation");return;}
  if(operation.equals("read_vehicle_state")){
   String command=args.get("command");
   if(command.equals("info-bt-adapter")){owner.diagnostic(command);result("","diagnostic_requested",false);return;}
   String section=command.equals("body-controller-state")?"vcsec":command.equals("read-state-envcheck")?"all":command.substring("read-state-".length());
   result("",owner.read(section)?"read_requested":"read_rejected",false);return;
  }
  boolean actuating=!(operation.equals("vehicle_command")&&"list-keys".equals(args.get("command")));
  if(duplicate&&actuating){result("","duplicate_actuation_rejected",false);return;}
  if(actuating&&!owner.controlsEnabled()){result("","controls_disabled",false);return;}
  if(operation.equals("key_management")){
   String action=args.get("action");
   if(!Set.of("deploy-key","generate-keys").contains(action)){result("","unsupported_key_action",false);return;}
   if(action.equals("deploy-key")&&!owner.hasExistingKey()){result("","no_existing_key",false);return;}
   boolean keyReady=action.equals("generate-keys")?owner.isNativeInitialized():owner.isReady();
   if(!keyReady){result("","vehicle_not_ready",false);return;}
  } else if(!owner.isReady()){result("","vehicle_not_ready",false);return;}
  if(pendingId!=null){result("","busy",false);return;}
  String raw;String value=null;
  switch(operation){
   case "vehicle_command":raw=args.get("command");break;
   case "key_management":raw=args.get("action");break;
   case "auto_seat_climate":raw="auto-seat-and-climate";value=args.get("value");break;
   case "set_charge_limit":raw="charging-set-limit";value=args.get("percent");break;
   case "set_charging_amps":raw="true".equals(args.get("override"))?"charging-set-amps-override":"charging-set-amps";value=args.get("amps");break;
   case "set_climate_temperature":raw="climate-set-temp";value=args.get("celsius");break;
   case "set_charging_schedule":raw="charging-schedule";value=args.get("minutes_after_midnight");break;
   case "set_media_volume":raw="media-set-volume";value=args.get("level");break;
   case "set_seat_heater":raw="heater-seat-"+args.get("seat");value=args.get("level");break;
   default:if(!operation.startsWith("set_")){result("","unsupported_operation",false);return;}raw=operation.substring(4);value=args.get("value");
  }
  if(raw.equals("software-update-start")){result("","requires_explicit_update_delay",false);return;}
  if(raw.equals("autosecure-modelx")&&vin.charAt(3)!='X'){result("","wrong_vehicle_model",false);return;}
  String id=UUID.randomUUID().toString();pendingId=id;pendingOperation=raw;deadline=owner.now()+180000;
  boolean queued=owner.execute(id,raw,value==null?new String[0]:new String[]{value});
  if(!queued&&id.equals(pendingId)){pendingId=null;pendingOperation=null;result(id,"native_rejected",false);}
 }
 public void onCommandResult(String id,String status,boolean acknowledged){
  if(closed||id==null||!id.equals(pendingId))return;
  String operation=pendingOperation;pendingId=null;pendingOperation=null;
  result(id,acknowledged?"acknowledged":("rejected".equals(status)?"rejected":"failed"),acknowledged);
  if(acknowledged&&"generate-keys".equals(operation)){owner.onKeyRegenerated();refreshAvailability();}
  else if(acknowledged&&owner.isReady())owner.read(readback(operation));
 }
 private static String readback(String op){
  if(op.contains("climate")||op.contains("heater"))return "climate";
  if(op.contains("charg"))return "charge";
  if(op.contains("media"))return "media";
  if(op.contains("software"))return "software";
  if(op.equals("drive"))return "drive";
  if(op.equals("deploy-key")||op.equals("wake")||op.equals("ping")||op.equals("list-keys"))return "vcsec";
  return "closure";
 }
 public void onTelemetry(String section,Map<String,String> values){
  if(closed||section==null||values==null)return;
  Map<String,String> current=new LinkedHashMap<>(values);
  if(section.equals("vcsec")){current.put("binary_sensor/presence","true");current.put("binary_sensor/presence_bc","true");current.put("device_tracker/presence_bc","true");}
  for(String old:states.getOrDefault(section,Map.of()).keySet())if(!current.containsKey(old)&&section.equals(topicSources.get(old))){observations.remove(old);topicValues.remove(old);topicSources.remove(old);}
  states.put(section,Collections.unmodifiableMap(current));
  for(Map.Entry<String,String> value:current.entrySet())if(value.getValue()!=null){observations.put(value.getKey(),owner.now());topicValues.put(value.getKey(),value.getValue());topicSources.put(value.getKey(),section);}
  if(connected&&owner.isReady())for(Map.Entry<String,String> value:current.entrySet())if(value.getValue()!=null)publish(root+"/"+value.getKey(),value.getValue(),true);
  refreshAvailability();
 }
 private boolean fresh(String topic){Long time=observations.get(topic);if(time==null)return false;long age=owner.now()-time;long ttl=topic.contains("presence")?Math.min(60000L,owner.freshnessMillis()):owner.freshnessMillis();return age>=0&&age<=ttl;}
 private void replayFreshStates(){
  if(!owner.isReady())return;
  for(Map.Entry<String,String> value:topicValues.entrySet())if(fresh(value.getKey()))publish(root+"/"+value.getKey(),value.getValue(),true);
 }
 public void refreshAvailability(){
  if(closed)return;
  if(!owner.isReady()){observations.clear();topicValues.clear();topicSources.clear();states.clear();}
  if(pendingId!=null&&owner.now()>=deadline){String id=pendingId;pendingId=null;pendingOperation=null;owner.abortTimedOutCommand();result(id,"timeout_outcome_unknown",false);}
  if(!connected)return;
  for(LegacyMqttDiscoveryCatalog.Definition d:definitions){
   boolean ready=d.isSupported()&&!d.requiresConfirmation();String suffix=d.getStateSuffix();
   if(suffix.startsWith("variables/")){ /* Persisted policy is independent of vehicle reachability. */ }
   else if("deploy-key".equals(d.getObjectId()))ready=ready&&owner.isReady()&&owner.hasExistingKey();
   else if("regenerate-keys".equals(d.getObjectId()))ready=ready&&owner.isNativeInitialized();
   else if(!suffix.isEmpty())ready=ready&&owner.isReady()&&fresh(suffix)&&topicValues.containsKey(suffix);
   else ready=ready&&owner.isReady();
   if(d.isActuating())ready=ready&&owner.controlsEnabled();
   String value=ready?"online":"offline";
   if(!value.equals(availability.get(d.getAvailabilityTopic()))&&publish(d.getAvailabilityTopic(),value,true))availability.put(d.getAvailabilityTopic(),value);
  }
 }
 public void close(){if(closed)return;if(connected)publish(root+"/availability","offline",true);closed=true;connected=false;if(pendingId!=null){pendingId=null;pendingOperation=null;owner.abortTimedOutCommand();}try{transport.disconnect();}catch(RuntimeException ignored){deliveryError=true;}}
 private void result(String id,String status,boolean acknowledged){
  // Only generated IDs and internal constant statuses are serialized, never untrusted payloads.
  lastResult="{\"request_id\":\""+id+"\",\"status\":\""+status+"\",\"acknowledged\":"+acknowledged+"}";
  if(connected&&!closed)publish(root+"/command_result",lastResult,false);
 }
 private boolean publish(String topic,String payload,boolean retained){try{transport.publish(topic,payload,1,retained);return true;}catch(RuntimeException ignored){deliveryError=true;return false;}}
 private void subscribe(String topic){try{transport.subscribe(topic,1);}catch(RuntimeException ignored){deliveryError=true;}}
}
