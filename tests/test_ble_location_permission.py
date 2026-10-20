"""Vehicle beacon delivery must not opt into Android's location denylist."""
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/org/opencharge/teslable'
ANDROID = '{http://schemas.android.com/apk/res/android}'


def method(path, signature):
    source = path.read_text()
    start = source.index(signature)
    opening = source.index('{', start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


class BleLocationPermissionTest(unittest.TestCase):
    def run_harness(self, methods, checks):
        java = '''import java.util.*;
public class PermissionHarness {
  static class Build { static class VERSION { static int SDK_INT=34; } }
  static class Manifest { static class permission {
    static final String BLUETOOTH_SCAN="scan", BLUETOOTH_CONNECT="connect",
      ACCESS_FINE_LOCATION="fine", ACCESS_COARSE_LOCATION="coarse";
  } }
  static class PackageManager { static final int PERMISSION_GRANTED=0; }
  final Set<String> granted=new HashSet<>();
  List<String> requested=new ArrayList<>();
  Runnable pendingAfterPermissions;
  boolean ran;
  int checkSelfPermission(String permission){return granted.contains(permission)?0:-1;}
  void requestPermissions(String[] permissions,int request){requested=Arrays.asList(permissions);}
  METHODS
  public static void main(String[] args){
    PermissionHarness h=new PermissionHarness();
    CHECKS
    System.out.print("PASS");
  }
}'''.replace('METHODS', methods).replace('CHECKS', checks)
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'PermissionHarness.java'
            path.write_text(java)
            p = subprocess.run(['javac', '-d', temp, str(path)], capture_output=True, text=True)
            self.assertEqual(p.returncode, 0, p.stderr)
            p = subprocess.run(['java', '-cp', temp, 'PermissionHarness'], capture_output=True, text=True)
            self.assertEqual(p.returncode, 0, p.stderr)
            self.assertEqual(p.stdout, 'PASS')

    def test_manifest_does_not_filter_tesla_beacons_on_modern_android(self):
        root = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
        permissions = {e.get(ANDROID + 'name'): e for e in root.findall('uses-permission')}
        scan = permissions['android.permission.BLUETOOTH_SCAN']
        self.assertNotIn('neverForLocation', scan.get(ANDROID + 'usesPermissionFlags', ''),
                         'Android hides the verified Tesla advertisement from disavowed-location scans')
        for name in ['ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION']:
            self.assertIn('android.permission.' + name, permissions)
            self.assertIsNone(permissions['android.permission.' + name].get(ANDROID + 'maxSdkVersion'),
                              'Location permission must be requestable on Android 12+')

    def test_service_cannot_claim_scan_readiness_without_precise_location(self):
        self.run_harness(method(JAVA / 'TeslaBleService.java', 'private boolean hasBlePermissions('), '''
          h.granted.add("scan"); h.granted.add("connect");
          if(h.hasBlePermissions()) throw new AssertionError("Bluetooth permissions alone cannot deliver filtered vehicle beacon");
          h.granted.add("coarse");
          if(h.hasBlePermissions()) throw new AssertionError("Approximate-only location cannot satisfy scan permission");
          h.granted.add("fine");
          if(!h.hasBlePermissions()) throw new AssertionError("Fully authorized scanner rejected");
          h.granted.remove("scan");
          if(h.hasBlePermissions()) throw new AssertionError("Missing Bluetooth permission ignored");
          Build.VERSION.SDK_INT=30;
          if(!h.hasBlePermissions()) throw new AssertionError("Legacy precise-location scanner rejected");
        ''')

    def test_ui_requests_both_location_permissions_on_android_12_plus(self):
        self.run_harness(method(JAVA / 'MainActivity.java', 'private void withPermissions('), '''
          h.granted.add("scan"); h.granted.add("connect");
          h.withPermissions(()->h.ran=true);
          if(h.ran) throw new AssertionError("Scan ran before location permission");
          if(!h.requested.contains("fine") || !h.requested.contains("coarse"))
            throw new AssertionError("Android 12+ must request fine and coarse location together");
          h.granted.add("fine"); h.granted.add("coarse");
          h.withPermissions(()->h.ran=true);
          if(!h.ran) throw new AssertionError("Authorized action did not run");
        ''')


if __name__ == '__main__':
    unittest.main()
