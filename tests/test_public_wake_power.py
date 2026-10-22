import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class PublicWakePowerTests(unittest.TestCase):
 def test_power_exemption_is_explicit_ui_optin_not_a_silent_device_mutation(self):
  manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text();ui=(ROOT/'app/src/main/java/org/opencharge/teslable/PublicWakeSettingsActivity.java').read_text()
  self.assertIn('android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',manifest)
  self.assertIn('ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',ui);self.assertIn('isIgnoringBatteryOptimizations',ui)
  self.assertIn('power.setOnClickListener',ui);self.assertIn('device may suspend the gateway',ui)
  service=(ROOT/'app/src/main/java/org/opencharge/teslable/TeslaBleService.java').read_text()
  self.assertNotIn('ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',service)
