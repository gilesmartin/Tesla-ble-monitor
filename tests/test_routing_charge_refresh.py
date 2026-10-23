import os,re,subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SERVICE=ROOT/'app/src/main/java/org/opencharge/teslable/TeslaBleService.java'
def method(text,name):
 m=re.search(r'    (?:@Override )?(?:public|private) (?:static )?(?:void|boolean|Double|long|String) '+name+r'\(',text)
 if not m:return ''
 start=m.start();brace=text.index('{',m.end());depth=1;end=brace+1
 while depth:
  depth+=(text[end]=='{')-(text[end]=='}');end+=1
 return text[start:end]
class RoutingChargeRefreshTests(unittest.TestCase):
 def run_case(self,mode):
  text=SERVICE.read_text();helper=method(text,'refreshRoutingChargeIfDue')
  methods='\n'.join(method(text,n)for n in ['requestCharge','onTelemetry','onLegacyTelemetry','speedMph','driveLeaseIsActive','refreshRoutingChargeIfDue','adaptivePollingMode','activeFreshnessMillis','driveGear','driveSpeed','chargeAgeMillis','observeVehicleActivity','clearDriveState','runDrivePoll','onTelemetryAccessed','runBootstrapRead'])
  start=text.index('    private final Runnable adaptiveChargeTask =');brace=text.index('{',start);depth=1;end=brace+1
  while depth:
   depth+=(text[end]=='{')-(text[end]=='}');end+=1
  methods+='\n'+text[start:end]+';'
  task='this::refreshRoutingChargeIfDue' if helper else '()->{}'
  source=r'''import java.util.*;
interface Sink {void onLegacyTelemetry(String json);void onTelemetry(String json);}
class JSONException extends Exception {}
class JSONObject {
 static final Object NULL=new Object(); final String kind; final Map<String,Object> values=new LinkedHashMap<>();
 JSONObject(String kind)throws JSONException{this.kind=kind;
  if(kind.equals("EVENT")){values.put("section","drive");values.put("values",new JSONObject("DRIVE"));}
  if(kind.equals("DRIVE")){values.put("shift_state",RoutingChargeHarness.gear);values.put("speed",RoutingChargeHarness.speed);}
  if(kind.equals("CHARGE"))values.put("charging",RoutingChargeHarness.charging);
 }
 Object opt(String k){return values.get(k);} String optString(String k,String d){Object o=opt(k);return o instanceof String?(String)o:d;}
 JSONObject optJSONObject(String k){Object o=opt(k);return o instanceof JSONObject?(JSONObject)o:null;}
 Iterator<String> keys(){return values.keySet().iterator();} public String toString(){return kind;}
}
class Looper {static final Looper OWNER=new Looper();static Looper myLooper(){return OWNER;}static Looper getMainLooper(){return OWNER;}}
class SystemClock {static long elapsedRealtime(){return RoutingChargeHarness.clock;}}
class LegacyMqttStateMapper {static Map<String,String> map(String s,Map<String,String>m){return m;}}
class LegacyMqttPublisher {boolean pending;boolean hasPendingCommand(){return pending;}void onTelemetry(String s,Map<String,String>m){}}
public class RoutingChargeHarness implements Sink {
 static long clock=1000000;static String gear="D";static double speed=30;static boolean charging=false;
 boolean publicWakeInFlight=false,ready=true,nativeInitialized=true,pollEnabled=true,pollInFlight=false,driveReadInFlight=true,driveSubscriptionActive=true;
 long driveSubscriptionExpiresAtElapsedMs=clock+60000,lastDriveStateAtElapsedMs=clock,lastChargeAtElapsedMs=-1,lastChargeRequestElapsedMs=-1;
 String currentVin="SYNTHETIC",nativeVin="SYNTHETIC",legacyMqttVin="SYNTHETIC",lastChargeJson="CHARGE",lastDriveStateJson;
 Boolean vehicleAsleep,vehicleMoving,vehicleUserPresent;long lastVehicleStatusAtElapsedMs=-1,lastChargeFreshnessMs=300000;boolean driveProbeAllowed,vcsecProbeInFlight,serviceStarted=true;long lastTelemetryAccessElapsedMs=-1,bootstrapReadDeadlineMs=-1;String lastPollFailure="";
 int reads,pollFailures,driveReadFailures,pollIntervalSeconds=720; LegacyMqttPublisher legacyMqttPublisher;
 long lastChargeAtMs,lastScheduledPoll=-1,lastScheduledDrive=-1;final Object stateLock=new Object();String authenticationState;boolean keyPresent;
 static final String PREF_LAST_CHARGE="charge",PREF_LAST_CHARGE_AT="timestamp";
 interface Listener {void onTelemetry(String json);} Listener listener;
 class Preferences {Preferences edit(){return this;}Preferences putString(String k,String v){return this;}Preferences putLong(String k,long v){return this;}void apply(){}}
 final Preferences preferences=new Preferences();void updateMqttTelemetry(){}void notifyState(){}String buildTelemetryJson(){return "SAFE";}void schedulePoll(long delay){lastScheduledPoll=delay;}
 static final long DRIVE_POLL_INTERVAL_MS=250,DRIVE_FRESHNESS_MS=1500,DRIVE_READ_TIMEOUT_MS=1500,DRIVE_SUBSCRIPTION_LEASE_MS=15000,CHARGE_TIMEOUT_MS=12000,ROUTING_CHARGE_INTERVAL_MS=30000,ROUTING_CHARGE_MAX_BACKOFF_MS=120000,ACTIVE_CHARGE_INTERVAL_MS=10000,CHARGING_CHARGE_INTERVAL_MS=15000;
 final Runnable chargeTimeout=()->{},driveTimeoutTask=()->{},drivePollTask=()->{},vcsecProbeTimeout=()->{};
 final Runnable bootstrapReadTask=this::runBootstrapRead;
 final Runnable routingChargeTask=TASK;
 final Handler main=new Handler();final Tesla tesla=new Tesla();
 class Handler {
 final ArrayDeque<Runnable>q=new ArrayDeque<>();final TreeMap<Long,List<Runnable>>timed=new TreeMap<>();
 Looper getLooper(){return Looper.OWNER;}
 void removeCallbacks(Runnable r){q.removeIf(x->x==r);for(var l:timed.values())l.removeIf(x->x==r);timed.entrySet().removeIf(e->e.getValue().isEmpty());}
 void postDelayed(Runnable r,long ms){timed.computeIfAbsent(clock+ms,k->new ArrayList<>()).add(r);}
 void post(Runnable r){q.add(r);}
 void flush(){int n=0;while(!q.isEmpty()){if(++n>1000)throw new AssertionError("immediate busy loop");q.remove().run();}}
 void advance(long target){int n=0;while(!timed.isEmpty()&&timed.firstKey()<=target){if(++n>2000)throw new AssertionError("timer busy loop");var e=timed.pollFirstEntry();clock=e.getKey();for(var a:e.getValue())a.run();flush();}clock=target;flush();}
 }
 class Tesla {void readChargeState(){reads++;main.post(()->onTelemetry("CHARGE"));}boolean readLegacyState(String section){reads++;if(section.equals("charge")||section.equals("all"))main.post(()->onTelemetry("CHARGE"));return true;}}
 boolean isValidVin(String v){return true;}void scheduleDrivePoll(long ms){lastScheduledDrive=ms;}void finishDriveReadFailure(){driveReadInFlight=false;}void log(String m){}void finishChargeFailure(String s){throw new AssertionError(s);}
'''.replace('TASK',task)+methods+r'''
 void drive(){driveReadInFlight=true;onLegacyTelemetry("EVENT");main.flush();}
 public static void main(String[]args)throws Exception{
  RoutingChargeHarness s=new RoutingChargeHarness();String mode=args[0];
  if(mode.equals("publicwake")){s.driveReadInFlight=false;s.publicWakeInFlight=true;s.requestCharge(false);if(s.reads!=0)throw new AssertionError("manual charge overlapped explicit wake");s.publicWakeInFlight=false;s.requestCharge(false);if(s.reads!=1)throw new AssertionError("charge remained blocked after wake");return;}
  if(mode.equals("manual_owner")){s.requestCharge(false);if(s.reads!=0)throw new AssertionError("manual charge overlapped DriveState native owner");return;}
  if(mode.equals("accepted_sample")){s.drive();if(s.reads!=1)throw new AssertionError("initial overdue battery read missing");s.onTelemetry("CHARGE");if(s.lastChargeAtElapsedMs!=clock||s.pollInFlight)throw new AssertionError("accepted sample did not refresh monotonic battery state");if(s.lastScheduledPoll!=720000)throw new AssertionError("normal saved charge interval changed");clock+=1000;s.drive();if(s.reads!=1)throw new AssertionError("fresh accepted battery read was duplicated");clock+=30000;s.driveSubscriptionExpiresAtElapsedMs=clock+60000;s.drive();if(s.reads!=2)throw new AssertionError("next real battery read missing after driving interval");return;}
  if(mode.equals("driving")){s.drive();if(s.reads!=1)throw new AssertionError("fresh driving callback did not refresh overdue battery");return;}
  if(mode.equals("charging")){charging=true;s.driveReadInFlight=false;s.driveSubscriptionActive=false;s.lastChargeAtElapsedMs=clock-16000;s.refreshRoutingChargeIfDue();if(s.reads!=1)throw new AssertionError("fresh confirmed charging without phone did not adapt");return;}
  if(mode.equals("parked")){gear="P";speed=30;s.drive();if(s.reads!=0)throw new AssertionError("parked cadence accelerated from stale speed");return;}
  if(mode.equals("disabled")){s.pollEnabled=false;s.drive();if(s.reads!=0)throw new AssertionError("disabled battery polling restarted");return;}
  if(mode.equals("expired")){s.driveSubscriptionExpiresAtElapsedMs=clock-1;s.drive();if(s.reads!=0)throw new AssertionError("expired lease initiated battery read");return;}
  if(mode.equals("cadence")){s.lastChargeAtElapsedMs=clock-5000;s.drive();if(s.reads!=0)throw new AssertionError("fresh battery unnecessarily reread");clock+=6000;s.driveSubscriptionExpiresAtElapsedMs=clock+60000;s.drive();if(s.reads!=1)throw new AssertionError("battery not refreshed by driving deadline");s.pollInFlight=false;clock+=1000;s.drive();if(s.reads!=1)throw new AssertionError("duplicate battery request inside cadence");return;}
  if(mode.equals("backoff")){s.pollFailures=2;s.lastChargeRequestElapsedMs=clock-30000;s.drive();if(s.reads!=0)throw new AssertionError("failed battery request ignored bounded retry backoff");clock+=90000;s.driveSubscriptionExpiresAtElapsedMs=clock+60000;s.drive();if(s.reads!=1)throw new AssertionError("bounded battery retry never resumed");return;}
  if(mode.equals("command")){s.legacyMqttPublisher=new LegacyMqttPublisher();s.legacyMqttPublisher.pending=true;s.drive();if(s.reads!=0)throw new AssertionError("battery read overlapped command owner");return;}
  if(mode.equals("repeat_cadence")){s.drive();if(s.reads!=1)throw new AssertionError("initial read missing");s.onTelemetry("CHARGE");clock+=10001;s.driveSubscriptionExpiresAtElapsedMs=clock+60000;s.drive();if(s.reads!=2)throw new AssertionError("healthy second read still held by old 30-second floor");return;}
  if(mode.equals("freshness")){s.lastChargeFreshnessMs=30000;s.driveSubscriptionActive=false;if(s.activeFreshnessMillis()!=30000)throw new AssertionError("expired lease promoted old driving battery sample to fresh");return;}
  if(mode.equals("parked_quiet")){gear="P";speed=90; s.lastDriveStateJson="DRIVE";s.driveReadInFlight=false;s.runDrivePoll();if(s.reads!=0)throw new AssertionError("empty parked car was probed continuously");s.driveProbeAllowed=true;s.runDrivePoll();if(s.reads!=1)throw new AssertionError("new consumer initial probe missing");s.driveReadInFlight=false;s.runDrivePoll();if(s.reads!=1)throw new AssertionError("initial probe turned into perpetual parked reads");return;}
  if(mode.equals("occupied")){gear="P";speed=0;s.lastDriveStateJson="DRIVE";s.driveReadInFlight=false;s.vehicleAsleep=false;s.vehicleUserPresent=true;s.lastVehicleStatusAtElapsedMs=clock;s.runDrivePoll();if(s.reads!=1)throw new AssertionError("fresh awake occupancy did not enable drive acquisition");return;}
  if(mode.equals("sleep_quiet")){s.driveReadInFlight=false;s.vehicleAsleep=true;s.driveProbeAllowed=true;s.runDrivePoll();if(s.reads!=0)throw new AssertionError("sleeping vehicle initial probe ignored sleep state");return;}
  if(mode.equals("bootstrap")){s.driveReadInFlight=false;s.lastChargeAtElapsedMs=clock-60000;s.onTelemetryAccessed();s.main.flush();if(s.reads!=1)throw new AssertionError("new consumer did not bootstrap old cache");for(int i=0;i<20;i++){clock+=500;s.onTelemetryAccessed();s.main.flush();}if(s.reads!=1)throw new AssertionError("continuous parked GETs repeatedly bootstrapped reads");s.onTelemetry("CHARGE");clock+=60000;s.onTelemetryAccessed();s.main.flush();if(s.reads!=2)throw new AssertionError("later genuinely new consumer did not bootstrap");return;}
  if(mode.equals("timer_charging")){charging=true;s.driveSubscriptionActive=false;s.driveReadInFlight=false;s.lastChargeAtElapsedMs=clock;s.lastChargeJson="CHARGE";long start=clock;s.main.post(s.adaptiveChargeTask);s.main.flush();s.main.advance(start+14999);if(s.reads!=0)throw new AssertionError("charging timer fired early");s.main.advance(start+15000);if(s.reads!=1)throw new AssertionError("charging timer missing first 15s read without Drive callback");s.main.advance(start+30000);if(s.reads!=2)throw new AssertionError("charging timer missing second 15s read after native accepted sample");return;}
  if(mode.equals("timer_presence_expiry")){gear="P";speed=0;charging=false;s.driveSubscriptionActive=false;s.driveReadInFlight=false;s.lastChargeAtElapsedMs=clock;s.lastChargeJson="CHARGE";s.vehicleAsleep=false;s.vehicleUserPresent=true;s.lastVehicleStatusAtElapsedMs=clock;long start=clock;s.main.post(s.adaptiveChargeTask);s.main.flush();s.main.advance(start+30000);if(s.reads!=1)throw new AssertionError("occupied timer missing 30s read");s.main.advance(start+90000);if(s.reads!=1)throw new AssertionError("expired presence kept parked fast polling alive");return;}
  if(mode.equals("timer_busy")){charging=true;s.driveSubscriptionActive=false;s.driveReadInFlight=true;s.lastChargeAtElapsedMs=clock;s.lastChargeJson="CHARGE";long start=clock;s.main.post(s.adaptiveChargeTask);s.main.flush();s.main.advance(start+20000);if(s.reads!=0)throw new AssertionError("adaptive timer overlapped native owner");s.driveReadInFlight=false;s.main.advance(start+21000);if(s.reads!=1)throw new AssertionError("adaptive timer lost due read after owner released");return;}
  throw new AssertionError("unknown mode");
 }
}
'''
  with tempfile.TemporaryDirectory()as d:
   p=Path(d)/'RoutingChargeHarness.java';p.write_text(source)
   policy=Path(d)/'TelemetryPollingPolicy.java';policy.write_text((ROOT/'app/src/main/java/org/opencharge/teslable/TelemetryPollingPolicy.java').read_text().replace('package org.opencharge.teslable;',''))
   c=subprocess.run(['javac','-d',d,str(policy),str(p)],capture_output=True,text=True);self.assertEqual(c.returncode,0,c.stderr)
   r=subprocess.run(['java','-cp',d,'RoutingChargeHarness',mode],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
 def test_production_periodic_runnable_charges_twice_without_lease_or_drive_callback(self):self.run_case('timer_charging')
 def test_production_periodic_runnable_quiets_when_presence_proof_expires(self):self.run_case('timer_presence_expiry')
 def test_production_periodic_runnable_defers_owner_then_catches_up(self):self.run_case('timer_busy')
 def test_actual_second_driving_refresh_has_no_old_thirty_second_floor(self):self.run_case('repeat_cadence')
 def test_expired_lease_does_not_promote_old_active_sample(self):self.run_case('freshness')
 def test_empty_parked_consumer_does_not_perpetually_poll_infotaiment(self):self.run_case('parked_quiet')
 def test_fresh_awake_occupancy_allows_acquisition(self):self.run_case('occupied')
 def test_sleep_observation_blocks_initial_probe(self):self.run_case('sleep_quiet')
 def test_new_consumer_bootstraps_once_not_per_parked_get(self):self.run_case('bootstrap')
 def test_manual_charge_respects_explicit_public_wake_owner(self):self.run_case('publicwake')
 def test_manual_charge_respects_drive_native_owner(self):self.run_case('manual_owner')
 def test_fresh_driving_callback_refreshes_overdue_battery(self):self.run_case('driving')
 def test_confirmed_charging_without_phone_uses_fifteen_seconds(self):self.run_case('charging')
 def test_parked_does_not_accelerate_from_stale_speed(self):self.run_case('parked')
 def test_disabled_polling_remains_disabled(self):self.run_case('disabled')
 def test_expired_subscription_cannot_read_charge(self):self.run_case('expired')
 def test_driving_charge_is_bounded_to_ten_seconds(self):self.run_case('cadence')
 def test_failed_charge_read_has_bounded_backoff(self):self.run_case('backoff')
 def test_pending_command_remains_exclusive_owner(self):self.run_case('command')
 def test_accepted_native_sample_refreshes_monotonic_state_and_keeps_normal_timer(self):self.run_case('accepted_sample')
if __name__=='__main__':unittest.main()
