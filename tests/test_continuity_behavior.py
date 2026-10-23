import os,re,subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/org/opencharge/teslable'
def method(text,name):
 m=re.search(r'    (?:@Override )?(?:public|private) (?:static )?[\w<>]+ '+name+r'\(',text)
 if not m:raise AssertionError('missing production method '+name)
 b=text.index('{',m.end());d=1;e=b+1
 while d:d+=(text[e]=='{')-(text[e]=='}');e+=1
 return text[m.start():e]
class ContinuityBehaviorTests(unittest.TestCase):
 def java(self,files,main='org.opencharge.teslable.Harness',production=()):
  with tempfile.TemporaryDirectory()as tmp:
   paths=[]
   for name,body in files.items():
    p=Path(tmp)/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);paths.append(str(p))
   p=subprocess.run(['javac','-d',tmp,*[str(JAVA/x)for x in production],*paths],capture_output=True,text=True)
   self.assertEqual(p.returncode,0,p.stderr)
   p=subprocess.run(['java','-cp',tmp,main],capture_output=True,text=True,timeout=15)
   self.assertEqual(p.returncode,0,p.stdout+p.stderr)
 def test_actual_adaptive_policy_driving_charging_quiet_and_backoff(self):
  self.assertTrue((JAVA/'TelemetryPollingPolicy.java').exists(),'missing adaptive policy')
  self.java({'Harness.java':r"""package org.opencharge.teslable;
public class Harness {
 static void yes(boolean x){if(!x)throw new AssertionError();}
 public static void main(String[]a){
  yes(TelemetryPollingPolicy.mode(false,true,100,"D",30.,1000,false).equals("driving"));
  yes(TelemetryPollingPolicy.interval("driving",720)==10000);
  yes(TelemetryPollingPolicy.retryDelay(10000,0)==10000);
  yes(TelemetryPollingPolicy.retryDelay(10000,2)==40000);
  yes(TelemetryPollingPolicy.retryDelay(15000,8)==120000);
  yes(TelemetryPollingPolicy.mode(false,false,99999,"P",0.,1000,true).equals("charging"));
  yes(TelemetryPollingPolicy.mode(false,true,0,"P",99.,45001,true).equals("parked"));
  yes(TelemetryPollingPolicy.mode(false,false,0,"D",30.,0,false).equals("parked"));
  yes(TelemetryPollingPolicy.mode(false,true,1501,"D",30.,0,false).equals("parked"));
  yes(TelemetryPollingPolicy.mode(true,true,0,"D",30.,0,false).equals("sleeping"));
  yes(!TelemetryPollingPolicy.driveProbe(false,false,false,0,"P",99.));
  yes(!TelemetryPollingPolicy.driveProbe(true,true,true,0,"D",30.));
  yes(TelemetryPollingPolicy.driveProbe(false,true,false,99999,"P",0.));
  yes(TelemetryPollingPolicy.driveProbe(false,false,true,99999,"P",0.));
  yes(!TelemetryPollingPolicy.driveProbe(false,false,false,15001,"D",30.));
  yes(TelemetryPollingPolicy.freshness("driving")==30000);
  yes(TelemetryPollingPolicy.freshness("charging")==45000);
  yes(TelemetryPollingPolicy.freshness("parked")<=300000);
  yes(TelemetryPollingPolicy.mode(false,true,0,"P",0.,0,false,true).equals("occupied"));
  yes(TelemetryPollingPolicy.mode(true,true,0,"P",0.,0,false,true).equals("sleeping"));
  yes(TelemetryPollingPolicy.interval("occupied",720)==30000);
  yes(TelemetryPollingPolicy.interval("parked",15)==120000);
 }
}
"""},production=['TelemetryPollingPolicy.java'])
 def test_actual_boot_and_update_receiver_gates_and_denied_start(self):
  files={
   'android/content/BroadcastReceiver.java':'package android.content;public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}',
   'android/content/Intent.java':'package android.content;public class Intent {public static final String ACTION_BOOT_COMPLETED="boot",ACTION_MY_PACKAGE_REPLACED="replace",ACTION_LOCKED_BOOT_COMPLETED="locked",ACTION_USER_UNLOCKED="unlock";String action;public Intent(Context c,Class<?>s){}public Intent(String a){action=a;}public Intent setAction(String a){action=a;return this;}public String getAction(){return action;}}',
   'android/content/Context.java':'package android.content;public class Context {public int starts;public boolean deny;public android.os.UserHandle getUser(){return new android.os.UserHandle();}public void startForegroundService(Intent i){if(deny)throw new IllegalStateException();starts++;}public void startService(Intent i){starts++;}}',
   'android/os/UserHandle.java':'package android.os;public class UserHandle {}',
   'android/os/Process.java':'package android.os;public class Process {public static UserHandle myUserHandle(){return new UserHandle();}}',
   'android/os/Build.java':'package android.os;public class Build {public static class VERSION {public static int SDK_INT=34;}public static class VERSION_CODES {public static final int O=26;}}',
   'android/util/Log.java':'package android.util;public class Log {public static int w(String t,String m){return 0;}}',
   'TeslaBleService.java':'package org.opencharge.teslable;import android.content.Context;public class TeslaBleService {static boolean saved;static final String ACTION_BOOT_RESTORE="restore";static boolean hasSavedReconnectIntent(Context c){return saved;}}',
   'Harness.java':r"""package org.opencharge.teslable;import android.content.*;
public class Harness {public static void main(String[]a){Context c=new Context();BootRestoreReceiver r=new BootRestoreReceiver();
 r.onReceive(c,null);r.onReceive(c,new Intent("unknown"));if(c.starts!=0)throw new AssertionError();
 r.onReceive(c,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));if(c.starts!=0)throw new AssertionError("explicit stop ignored");
 TeslaBleService.saved=true;r.onReceive(c,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));r.onReceive(c,new Intent(Intent.ACTION_BOOT_COMPLETED));if(c.starts!=2)throw new AssertionError("restore missing");
 c.deny=true;r.onReceive(c,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));if(!TeslaBleService.saved||c.starts!=2)throw new AssertionError("denial lost intent");}}
"""}
  self.java(files,production=['BootRestoreReceiver.java'])
 def test_actual_api_health_checks_recover_dead_listener_not_healthy_owner(self):
  s=(JAVA/'TeslaBleService.java').read_text();body=method(s,'checkServiceHealth')
  self.java({'Harness.java':r"""package org.opencharge.teslable;
public class Harness {boolean serviceStarted=true,apiAvailable;int starts,checks;Server apiServer;class Server {boolean live;Server(boolean l){live=l;}boolean isRunning(){return live;}}
 final Runnable apiRetryTask=()->{};static final long API_RETRY_MS=15000;class Handler {void postDelayed(Runnable r,long t){checks++;}}final Handler main=new Handler();void startApiServer(){starts++;apiServer=new Server(true);}
"""+body+r"""
 public static void main(String[]a){Harness h=new Harness();h.apiServer=h.new Server(true);h.checkServiceHealth();if(h.starts!=0||h.checks!=1)throw new AssertionError("healthy listener replaced");h.apiServer.live=false;h.checkServiceHealth();if(h.starts!=1)throw new AssertionError("dead listener never retried");h.serviceStarted=false;h.apiServer.live=false;h.checkServiceHealth();if(h.starts!=1)throw new AssertionError("stopped service restarted");}}
"""})
 def test_actual_lease_renewal_does_not_restart_parked_probes_or_release_busy_owner(self):
  s=(JAVA/'TeslaBleService.java').read_text();body=method(s,'onDriveSubscriptionChanged')+'\n'+method(s,'driveLeaseIsActive')
  self.java({'Harness.java':r"""package org.opencharge.teslable;import java.util.*;
class SystemClock {static long now=100000;static long elapsedRealtime(){return now;}}
public class Harness {boolean driveSubscriptionActive,driveProbeAllowed,ready=true,driveReadInFlight;long driveSubscriptionExpiresAtElapsedMs;static final long DRIVE_SUBSCRIPTION_LEASE_MS=15000;int probes;final Runnable drivePollTask=()->{},driveTimeoutTask=()->{};
 class Handler {List<Runnable>q=new ArrayList<>();void post(Runnable r){q.add(r);}void removeCallbacks(Runnable r){}void flush(){while(!q.isEmpty())q.remove(0).run();}}final Handler main=new Handler();void scheduleDrivePoll(long t){probes++;}void clearDriveState(){}
"""+body+r"""
 public static void main(String[]a){Harness h=new Harness();h.onDriveSubscriptionChanged(true);h.main.flush();if(h.probes!=1)throw new AssertionError();long end=h.driveSubscriptionExpiresAtElapsedMs;SystemClock.now+=1000;h.onDriveSubscriptionChanged(true);h.main.flush();if(h.probes!=1||h.driveSubscriptionExpiresAtElapsedMs<=end)throw new AssertionError("renewal reset probe deadline");h.driveReadInFlight=true;h.onDriveSubscriptionChanged(false);h.main.flush();if(!h.driveReadInFlight)throw new AssertionError("lease release unlocked in-flight native read");}}
"""})
 def test_actual_write_timeout_cannot_close_newer_write_or_gatt(self):
  s=(JAVA/'TeslaBleService.java').read_text();body=method(s,'flushWrites')
  m=re.search(r'    private final Runnable writeTimeoutTask = .*?\n    };',s,re.S)
  if m:body+=m.group(0).replace('private final Runnable writeTimeoutTask =','{ writeTimeoutTask =')+' }'
  self.java({'Harness.java':r"""package org.opencharge.teslable;import java.util.*;
class Build {static class VERSION {static int SDK_INT=34;}}
class BluetoothStatusCodes {static final int SUCCESS=0;}
class BluetoothGattCharacteristic {static final int WRITE_TYPE_DEFAULT=0;void setWriteType(int t){}void setValue(byte[]v){}}
class BluetoothGatt {int writeCharacteristic(BluetoothGattCharacteristic c,byte[]b,int t){return 0;}boolean writeCharacteristic(BluetoothGattCharacteristic c){return true;}}
public class Harness {boolean writing,ready=true;BluetoothGatt gatt=new BluetoothGatt(),activeWriteGatt;BluetoothGattCharacteristic tx=new BluetoothGattCharacteristic();ArrayDeque<byte[]>writes=new ArrayDeque<>();long writeGeneration,activeWriteGeneration;static final long GATT_WRITE_TIMEOUT_MS=15000;Runnable writeTimeoutTask;int failures;
 class Handler {void postDelayed(Runnable r,long t){}void removeCallbacks(Runnable r){}void post(Runnable r){r.run();}}final Handler main=new Handler();void failConnection(String s){failures++;}void log(String s){}
"""+body+r"""
 public static void main(String[]a){Harness h=new Harness();h.writes.add(new byte[]{1});h.flushWrites();Runnable old=h.writeTimeoutTask;h.writing=false;h.writes.add(new byte[]{2});h.flushWrites();old.run();if(h.failures!=0)throw new AssertionError("stale deadline closed newer write");h.writeTimeoutTask.run();if(h.failures!=1)throw new AssertionError("lost write never recovered");Runnable second=h.writeTimeoutTask;h.gatt=new BluetoothGatt();h.writing=false;h.writes.add(new byte[]{3});h.flushWrites();second.run();if(h.failures!=1)throw new AssertionError("old GATT deadline closed new connection");}}
"""})
 def test_unavailable_car_respects_quiet_floor_without_changing_saved_setting(self):
  m=method((JAVA/'TeslaBleService.java').read_text(),'finishChargeFailure')
  self.java({'Harness.java':'''package org.opencharge.teslable;
public class Harness {boolean pollInFlight=true,pollEnabled=true,ready=true;int pollIntervalSeconds=15,pollFailures=0;String lastPollFailure;long delay;Object chargeTimeout=new Object();Handler main=new Handler();static class Handler{void removeCallbacks(Object r){}}void log(String s){}void updateMqttTelemetry(){}void notifyState(){}void schedulePoll(long d){delay=d;}
'''+m+''' public static void main(String[]a){Harness h=new Harness();h.finishChargeFailure("unavailable");if(h.delay<120000||h.pollIntervalSeconds!=15)throw new AssertionError("quiet floor bypassed or saved setting changed");}}
'''})
if __name__=='__main__':unittest.main()
