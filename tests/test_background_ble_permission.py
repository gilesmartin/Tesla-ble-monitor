"""Background discovery needs all-time location; runtime fine alone is insufficient."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
import test_ble_location_permission as baseline
ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/org/opencharge/teslable'
A = '{http://schemas.android.com/apk/res/android}'
class BackgroundBlePermissionTest(unittest.TestCase):
    def test_manifest_allows_user_to_grant_background_beacon_delivery(self):
        root = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
        permissions = {n.get(A+'name') for n in root.findall('uses-permission')}
        self.assertIn('android.permission.ACCESS_BACKGROUND_LOCATION', permissions,
                      'Fine location while-in-use is rejected for connected-device-only background scans')
        self.assertNotIn('android.permission.ACCESS_MEDIA_LOCATION', permissions)
    def test_auto_reconnect_checks_background_access_but_manual_discovery_is_preserved(self):
        service = (JAVA/'TeslaBleService.java').read_text()
        self.assertIn('private boolean hasBackgroundBlePermission(', service)
        permission = baseline.method(JAVA/'TeslaBleService.java', 'private boolean hasBackgroundBlePermission(')
        harness = baseline.BleLocationPermissionTest()
        # Existing production-method harness is extended only with the Android permission constant.
        original = harness.run_harness
        def execute(methods, checks):
            methods = methods.replace('Manifest.permission.ACCESS_BACKGROUND_LOCATION', '"background"')
            original(methods, checks)
        execute(permission, '''
            h.granted.add("fine"); h.granted.add("scan"); h.granted.add("connect");
            if(h.hasBackgroundBlePermission()) throw new AssertionError("While-in-use permission accepted for autonomous scans");
            h.granted.add("background");
            if(!h.hasBackgroundBlePermission()) throw new AssertionError("All-time grant rejected");
            h.granted.remove("background");
            Build.VERSION.SDK_INT=29;
            if(h.hasBackgroundBlePermission()) throw new AssertionError("Android 10 background restriction ignored");
            Build.VERSION.SDK_INT=28;
            if(!h.hasBackgroundBlePermission()) throw new AssertionError("Pre-background-permission Android rejected");
        ''')
        scan = baseline.method(JAVA/'TeslaBleService.java', 'private void beginScan(')
        self.assertIn('autoConnectAfterScan && !hasBackgroundBlePermission()', scan)
        self.assertIn('background_scan_permission_required', scan)
        self.assertLess(scan.index('hasBackgroundBlePermission()'), scan.index('scanner.startScan('))
        self.assertNotIn('requestPermissions(', scan)
        self.assertNotIn('regenerate', scan.lower())
    def test_ui_explains_separate_settings_consent_without_requesting_gps(self):
        ui = (JAVA/'MainActivity.java').read_text()
        self.assertIn('Background Bluetooth permission', ui)
        self.assertIn('showBackgroundBlePermissionHelp', ui)
        self.assertIn('Allow all the time', ui)
        self.assertIn('android.settings.APPLICATION_DETAILS_SETTINGS', ui)
        self.assertNotIn('getLastKnownLocation', ui)
        self.assertNotIn('requestLocationUpdates', ui)
        ordinary = baseline.method(JAVA/'MainActivity.java', 'private void withPermissions(')
        self.assertNotIn('ACCESS_BACKGROUND_LOCATION', ordinary,
                         'Android 11+ foreground and background grants must not be requested together')
if __name__ == '__main__': unittest.main()
