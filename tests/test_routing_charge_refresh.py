import os,re,subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SERVICE=ROOT/'app/src/main/java/org/opencharge/teslable/TeslaBleService.java'
def method(text,name):
 m=re.search(r'    (?:@Override )?(?:public|private) (?:static )?(?:void|boolean|Double) '+name+r'\(',text)
 if not m:return ''
 start=m.start();brace=text.index('{',m.end());depth=1;end=brace+1
 while depth:
  depth+=(text[end]=='{')-(text[end]=='}');end+=1
 return text[start:end]
class RoutingChargeRefreshTests(unittest.TestCase):
 def run_case(self,mode):
  text=SERVICE.read_text();helper=method(text,'refreshRoutingChargeIfDue')
  methods='\n'.join(method(text,n)for n in ['requestCharge','onTelemetry','onLegacyTelemetry','speedMph','driveLeaseIsActive','refreshRoutingChargeIfDue'])
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
 boolean ready=true,nativeInitialized=true,pollEnabled=true,pollInFlight=false,driveReadInFlight=true,driveSubscriptionActive=true;
 long driveSubscriptionExpiresAtElapsedMs=clock+60000,lastDriveStateAtElapsedMs=clock,lastChargeAtElapsedMs=-1,lastChargeRequestElapsedMs=-1;
 String currentVin="SYNTHETIC",nativeVin="SYNTHETIC",legacyMqttVin="SYNTHETIC",lastChargeJson="CHARGE",lastDriveStateJson;
 int reads,pollFailures,driveReadFailures,pollIntervalSeconds=720; LegacyMqttPublisher legacyMqttPublisher;
 long lastChargeAtMs,lastScheduledPoll=-1;final Object stateLock=new Object();String authenticationState;boolean keyPresent;
 static final String PREF_LAST_CHARGE="charge",PREF_LAST_CHARGE_AT="timestamp";
 interface Listener {void onTelemetry(String json);} Listener listener;
 class Preferences {Preferences edit(){return this;}Preferences putString(String k,String v){return this;}Preferences putLong(String k,long v){return this;}void apply(){}}
 final Preferences preferences=new Preferences();void updateMqttTelemetry(){}void notifyState(){}String buildTelemetryJson(){return "SAFE";}void schedulePoll(long delay){lastScheduledPoll=delay;}
 static final long DRIVE_POLL_INTERVAL_MS=1000,DRIVE_FRESHNESS_MS=5000,CHARGE_TIMEOUT_MS=12000,ROUTING_CHARGE_INTERVAL_MS=30000,ROUTING_CHARGE_MAX_BACKOFF_MS=120000;
 final Runnable chargeTimeout=()->{},driveTimeoutTask=()->{};
 final Runnable routingChargeTask=TASK;
 final Handler main=new Handler();final Tesla tesla=new Tesla();
 class Handler {final List<Runnable>q=new ArrayList<>();Looper getLooper(){return Looper.OWNER;}void post(Runnable r){q.add(r);}void removeCallbacks(Runnable r){q.removeIf(x->x==r);}void postDelayed(Runnable r,long ms){}void flush(){int n=0;while(!q.isEmpty()){if(++n>20)throw new AssertionError("callback spin");q.remove(0).run();}}}
 class Tesla {void readChargeState(){reads++;}boolean readLegacyState(String s){reads++;return true;}}
 boolean isValidVin(String v){return true;}void scheduleDrivePoll(long ms){}void log(String m){}void finishChargeFailure(String s){throw new AssertionError(s);}
'''.replace('TASK',task)+methods+r'''
 void drive(){driveReadInFlight=true;onLegacyTelemetry("EVENT");main.flush();}
 public static void main(String[]args)throws Exception{
  RoutingChargeHarness s=new RoutingChargeHarness();String mode=args[0];
  if(mode.equals("manual_owner")){s.requestCharge(false);if(s.reads!=0)throw new AssertionError("manual charge overlapped DriveState native owner");return;}
  if(mode.equals("accepted_sample")){s.drive();if(s.reads!=1)throw new AssertionError("initial overdue battery read missing");s.onTelemetry("CHARGE");if(s.lastChargeAtElapsedMs!=clock||s.pollInFlight)throw new AssertionError("accepted sample did not refresh monotonic battery state");if(s.lastScheduledPoll!=720000)throw new AssertionError("normal saved charge interval changed");clock+=1000;s.drive();if(s.reads!=1)throw new AssertionError("fresh accepted battery read was duplicated");clock+=30000;s.driveSubscriptionExpiresAtElapsedMs=clock+60000;s.drive();if(s.reads!=2)throw new AssertionError("next real battery read missing after driving interval");return;}
  if(mode.equals("driving")){s.drive();if(s.reads!=1)throw new AssertionError("fresh driving callback did not refresh overdue battery");return;}
  if(mode.equals("charging")){charging=true;s.drive();if(s.reads!=0)throw new AssertionError("charging cadence accelerated");return;}
  if(mode.equals("parked")){gear="P";speed=30;s.drive();if(s.reads!=0)throw new AssertionError("parked cadence accelerated from stale speed");return;}
  if(mode.equals("disabled")){s.pollEnabled=false;s.drive();if(s.reads!=0)throw new AssertionError("disabled battery polling restarted");return;}
  if(mode.equals("expired")){s.driveSubscriptionExpiresAtElapsedMs=clock-1;s.drive();if(s.reads!=0)throw new AssertionError("expired lease initiated battery read");return;}
  if(mode.equals("cadence")){s.lastChargeAtElapsedMs=clock-10000;s.drive();if(s.reads!=0)throw new AssertionError("fresh battery unnecessarily reread");clock+=20000;s.driveSubscriptionExpiresAtElapsedMs=clock+60000;s.drive();if(s.reads!=1)throw new AssertionError("battery not refreshed by driving deadline");s.pollInFlight=false;clock+=1000;s.drive();if(s.reads!=1)throw new AssertionError("duplicate battery request inside cadence");return;}
  if(mode.equals("backoff")){s.pollFailures=2;s.lastChargeRequestElapsedMs=clock-30000;s.drive();if(s.reads!=0)throw new AssertionError("failed battery request ignored bounded retry backoff");clock+=90000;s.driveSubscriptionExpiresAtElapsedMs=clock+60000;s.drive();if(s.reads!=1)throw new AssertionError("bounded battery retry never resumed");return;}
  if(mode.equals("command")){s.legacyMqttPublisher=new LegacyMqttPublisher();s.legacyMqttPublisher.pending=true;s.drive();if(s.reads!=0)throw new AssertionError("battery read overlapped command owner");return;}
  throw new AssertionError("unknown mode");
 }
}
'''
  with tempfile.TemporaryDirectory()as d:
   p=Path(d)/'RoutingChargeHarness.java';p.write_text(source)
   c=subprocess.run(['javac','-d',d,str(p)],capture_output=True,text=True);self.assertEqual(c.returncode,0,c.stderr)
   r=subprocess.run(['java','-cp',d,'RoutingChargeHarness',mode],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
 def test_manual_charge_respects_drive_native_owner(self):self.run_case('manual_owner')
 def test_fresh_driving_callback_refreshes_overdue_battery(self):self.run_case('driving')
 def test_charging_preserves_normal_cadence(self):self.run_case('charging')
 def test_parked_does_not_accelerate_from_stale_speed(self):self.run_case('parked')
 def test_disabled_polling_remains_disabled(self):self.run_case('disabled')
 def test_expired_subscription_cannot_read_charge(self):self.run_case('expired')
 def test_driving_charge_is_bounded_to_thirty_seconds(self):self.run_case('cadence')
 def test_failed_charge_read_has_bounded_backoff(self):self.run_case('backoff')
 def test_pending_command_remains_exclusive_owner(self):self.run_case('command')
 def test_accepted_native_sample_refreshes_monotonic_state_and_keeps_normal_timer(self):self.run_case('accepted_sample')
if __name__=='__main__':unittest.main()
