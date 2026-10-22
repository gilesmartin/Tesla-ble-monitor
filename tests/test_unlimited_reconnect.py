"""Execute the real service retry scheduler with JVM timer/preferences stand-ins."""
import re
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / 'app/src/main/java/org/opencharge/teslable/TeslaBleService.java'


def method(source, name):
    start = re.search(r'private (?:void|boolean) '+name+r'\(',source).start()
    opening = source.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


class UnlimitedReconnectTests(unittest.TestCase):
    def run_scheduler(self, checks):
        source = SERVICE.read_text()
        constants = '\n'.join(re.findall(r'private static final (?:int|long) (?:MAX_RECONNECT_ATTEMPTS|INITIAL_RECONNECT_MS|MAX_RECONNECT_MS) = [^;]+;', source))
        java = '''public class RetryHarness {
          CONSTANTS
          boolean desiredConnection = true, publicWakeLease = false;
          int reconnectAttempts;
          String connectionState;
          static final String PREF_DESIRED="desired", PREF_RETRIES="retries";
          final Runnable reconnectTask=()->{};
          final Timer main=new Timer();
          final Prefs preferences=new Prefs();
          void notifyState(){} void log(String s){}
          static class Timer { int scheduled, removed; long delay;
            void removeCallbacks(Runnable r){removed++;}
            void postDelayed(Runnable r,long d){scheduled++;delay=d;}
          }
          static class Prefs { Prefs edit(){return this;}
            Prefs putBoolean(String k,boolean v){return this;}
            Prefs putInt(String k,int v){return this;} void apply(){}
          }
          METHOD
          public static void main(String[] args) {
            RetryHarness h=new RetryHarness();
            CHECKS
            System.out.print("PASS");
          }
        }'''.replace('CONSTANTS', constants).replace('METHOD', method(source, 'connectionWanted')+'\n'+method(source, 'scheduleReconnect')).replace('CHECKS', checks)
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'RetryHarness.java'
            path.write_text(java)
            compile_result = subprocess.run(['javac', '-d', temp, str(path)], capture_output=True, text=True)
            self.assertEqual(compile_result.returncode, 0, compile_result.stderr)
            result = subprocess.run(['java', '-cp', temp, 'RetryHarness'], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, 'PASS')

    def test_retries_continue_past_old_limit_and_counter_cannot_overflow(self):
        self.run_scheduler('''
          for(int i=0;i<1000;i++) {
            h.scheduleReconnect();
            if(!h.desiredConnection || h.main.scheduled!=i+1)
              throw new AssertionError("Retry stopped after " + i + " failures");
            if(h.main.delay<5000 || h.main.delay>60000)
              throw new AssertionError("Unbounded/busy retry delay");
          }
          h.reconnectAttempts=Integer.MAX_VALUE;
          h.scheduleReconnect();
          if(!h.desiredConnection || h.main.scheduled!=1001 || h.reconnectAttempts<0)
            throw new AssertionError("Counter overflow or eventual retry cutoff");
        ''')

    def test_unavailable_bluetooth_and_permissions_preserve_reconnect_intent(self):
        source = SERVICE.read_text()
        scan = method(source, 'beginScan')
        permission_gate = scan[:scan.index('boolean bluetoothEnabled;')]
        bluetooth_gate = scan[scan.index('if (!bluetoothEnabled)'):scan.index('if (scanning)')]
        connect = method(source, 'connectSelectedDevice')
        connect_permission_gate = connect[connect.index('if (!hasBlePermissions())'):connect.index('stopScan();')]
        for gate in (permission_gate, bluetooth_gate, connect_permission_gate):
            self.assertNotIn('desiredConnection = false', gate)
            self.assertIn('scheduleReconnect()', gate)

    def test_explicit_disconnect_intent_prevents_retry(self):
        self.run_scheduler('''
          h.desiredConnection=false;
          h.scheduleReconnect();
          if(h.main.scheduled!=0)throw new AssertionError("Explicit disconnect ignored");
        ''')


    def test_explicit_wake_lease_does_not_enable_persistent_connection_intent(self):
        self.run_scheduler('h.desiredConnection=false;h.publicWakeLease=true;h.scheduleReconnect();if(h.main.scheduled!=1||h.desiredConnection)throw new AssertionError("temporary wake lease changed saved intent");h.publicWakeLease=false;h.scheduleReconnect();if(h.main.scheduled!=1)throw new AssertionError("retry survived expired wake lease");')

if __name__ == '__main__':
    unittest.main()
