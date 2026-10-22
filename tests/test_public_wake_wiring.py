import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];JAVA=ROOT/'app/src/main/java/org/opencharge/teslable'
class PublicWakeWiringTests(unittest.TestCase):
 def test_optin_recovery_ui_and_sole_owner_wiring(self):
  self.assertTrue((JAVA/'PublicWakeSettingsActivity.java').exists(),'missing opt-in setup UI')
  source=(JAVA/'TeslaBleService.java').read_text()
  for text in ['ACTION_PUBLIC_WAKE_RECONFIGURE','PublicWakeCoordinator.Owner','tesla.executeLegacy(id, "wake", new String[0])','publicWakeCoordinator.result','connectionWanted()','publicWakeInFlight']:
   self.assertIn(text,source)
  receiver=(JAVA/'PublicWakeBootReceiver.java').read_text()
  for text in ['ACTION_BOOT_COMPLETED','ACTION_MY_PACKAGE_REPLACED','config.enabled()','config.configured()']:
   self.assertIn(text,receiver)
  config=(JAVA/'PublicWakeConfigStore.java').read_text();self.assertIn('getBoolean("enabled",false)',config)
  self.assertIn('tesla_ble_public_wake_secrets_v1',config);self.assertNotIn('tesla_ble_monitor_local_key_v1',config)
