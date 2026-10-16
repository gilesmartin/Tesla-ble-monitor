package org.opencharge.teslable;
import java.util.*;
public final class LegacyMqttPublisherHarness {
 static final String TEST_VIN="5YJ3E1EA7JF000001";
 static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 static final class Wire implements MqttBridgePublisher.Transport{
  Listener listener;List<String> subscriptions=new ArrayList<>(),records=new ArrayList<>();boolean connected;
  public void connect(String b,String id,String u,char[] pw,String will,String value,Listener callback){listener=callback;connected=true;callback.onConnected();}
  public void publish(String topic,String payload,int qos,boolean retained){records.add(topic+"="+payload);check(qos==1,"QoS1");}
  public void subscribe(String topic,int qos){subscriptions.add(topic);}
  public boolean isConnected(){return connected;}
  public void disconnect(){connected=false;}
  void input(String suffix,String payload,boolean retained,boolean duplicate){listener.onMessage("tesla_ble/"+TEST_VIN+"/"+suffix,payload,retained,duplicate);}
  boolean saw(String value){for(String record:records)if(record.contains(value))return true;return false;}
 }
 static final class Main implements LegacyMqttPublisher.Owner{
  ArrayDeque<Runnable> work=new ArrayDeque<>();long time=1000;boolean ready=true,controls=false,draining;int calls,reads,policies,aborts,diagnostics;String id,op,section;String[] args;
  public void post(Runnable r){work.add(r);}
  void run(Runnable r){post(r);while(!work.isEmpty()){draining=true;try{work.remove().run();}finally{draining=false;}}}
  public long now(){return time;}public long freshnessMillis(){return 90000;}
  public boolean isReady(){return ready;}public boolean controlsEnabled(){return controls;}
  public boolean execute(String id,String op,String[] args){check(draining,"native owner affinity");calls++;this.id=id;this.op=op;this.args=args;return true;}
  public boolean read(String section){check(draining,"read owner affinity");reads++;this.section=section;return true;}
  public boolean setPolicy(Boolean enabled,Integer seconds){policies++;return true;}
  public void diagnostic(String action){diagnostics++;}
  public void abortTimedOutCommand(){aborts++;ready=false;}
 }
 public static void main(String[] argv){
  Main owner=new Main();Wire wire=new Wire();LegacyMqttPublisher publisher=new LegacyMqttPublisher("tcp://127.0.0.1:1883",true,"fixture-user","fixture-password",TEST_VIN,wire,owner);
  owner.run(publisher::start);
  LegacyMqttDiscoveryCatalog.Definition modelX=null;
  for(LegacyMqttDiscoveryCatalog.Definition d:LegacyMqttDiscoveryCatalog.create(TEST_VIN))if("autosecure-modelx".equals(d.getObjectId()))modelX=d;
  check(modelX!=null&&!modelX.isSupported(),"Model-X control identity remains unavailable on Model 3");
  check(!wire.subscriptions.contains("tesla_ble/5YJ3E1EBXJF096057/#"),"no feedback wildcard");
  check(wire.records.size()>79,"discovery plus per-entity availability");
  switch(argv[0]){
   case "commands":
    wire.input("command","lock",false,false);check(owner.calls==0,"network callback cannot invoke native");owner.run(()->{});check(owner.calls==0&&wire.saw("controls_disabled"),"default gate");
    owner.controls=true;
    int beforeModelSpecific=owner.calls;
    wire.input("command","autosecure-modelx",false,false);owner.run(()->{});
    check(owner.calls==beforeModelSpecific&&wire.saw("wrong_vehicle_model"),"Model-X-only button cannot actuate a Model 3");
    wire.input("command","unlock",true,false);wire.input("command","unlock",false,true);owner.run(()->{});check(owner.calls==0,"retained/duplicate actuation rejected");
    wire.input("command","lock",false,false);owner.run(()->{});check(owner.calls==1&&owner.op.equals("lock"),"valid command reaches single owner");
    wire.input("command","honk",false,false);owner.run(()->{});check(owner.calls==1&&wire.saw("busy"),"bounded serial intake");
    String id=owner.id;owner.run(()->publisher.onCommandResult(id,"success",true));check(owner.reads==1&&owner.section.equals("closure"),"acknowledgement requests actual readback");
    wire.input("charging","start",false,false);owner.run(()->{});check(owner.op.equals("charging")&&owner.args[0].equals("start"),"charging wire translation");
    owner.time+=180001;owner.run(publisher::refreshAvailability);check(owner.aborts==1&&wire.saw("timeout_outcome_unknown"),"ambiguous timeout cancels native session without claiming non-execution");
    break;
   case "freshness":
    owner.run(()->publisher.onTelemetry("charge",Map.of("sensor/charge_state","0")));
    check(wire.saw("sensor/charge_state=0"),"present zero published");
    owner.time+=90001;int count=wire.records.size();owner.run(publisher::refreshAvailability);check(wire.records.size()>count,"stale availability transitions");
    count=wire.records.size();wire.listener.onMessage("homeassistant/status","online",false,false);owner.run(()->{});
    check(wire.records.subList(count,wire.records.size()).stream().noneMatch(r->r.endsWith("sensor/charge_state=0")),"birth must not replay stale charge");
    owner.run(()->publisher.onTelemetry("vcsec",Map.of("awake","false")));check(wire.saw("presence_bc=true"),"BCS response proves physical reachability independent of driver occupancy");
    owner.time++;
    owner.run(()->publisher.onTelemetry("closure",Map.of("binary_sensor/door_lock","true")));
    owner.time++;
    owner.run(()->publisher.onTelemetry("vcsec",Map.of("binary_sensor/door_lock","false")));
    int replayStart=wire.records.size();wire.listener.onMessage("homeassistant/status","online",false,false);owner.run(()->{});
    check(wire.records.subList(replayStart,wire.records.size()).stream().noneMatch(r->r.endsWith("binary_sensor/door_lock=true")),"older closure snapshot must not overwrite newer body-controller lock on birth");
    break;
   case "policy":
    wire.input("variables/polling","off",true,true);wire.input("variables/polling_interval","720",true,true);owner.run(()->{});check(owner.policies==2&&owner.calls==0,"idempotent retained policy accepted");
    wire.input("config","deploy-key",false,false);owner.run(()->{});check(owner.diagnostics==1&&owner.calls==0&&wire.saw("requires_local_confirmation"),"key preservation/confirmation boundary");
    wire.input("variables/polling_interval","3601",false,false);wire.input("heater-seat-random","med",false,false);owner.run(()->{});check(owner.calls==0&&owner.policies==2,"invalid input has no effects");
    wire.input("command","list-keys",false,false);owner.run(()->{});check(owner.calls==1&&owner.op.equals("list-keys"),"non-actuating key-list read allowed without controls");
    break;
   default:throw new AssertionError("unknown mode");
  }
  owner.run(publisher::close);int calls=owner.calls;wire.input("command","unlock",false,false);owner.run(()->{});check(owner.calls==calls,"closed controller cannot actuate");
  System.out.println("PASS legacy publisher "+argv[0]);
 }
}
