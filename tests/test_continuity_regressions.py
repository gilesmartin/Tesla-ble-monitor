import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
S=ROOT/'app/src/main/java/org/opencharge/teslable/TeslaBleService.java'
R=ROOT/'app/src/main/java/org/opencharge/teslable/BootRestoreReceiver.java'
class ContinuityRegression(unittest.TestCase):
 def test_package_replace_restores_normal_ble_independent_of_public_wake(self):
  r=R.read_text(); m=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
  self.assertIn('ACTION_MY_PACKAGE_REPLACED',r); self.assertIn('TeslaBleService.hasSavedReconnectIntent(context)',r)
  self.assertIn('MY_PACKAGE_REPLACED',m)
 def test_gatt_write_deadline_is_session_owned_and_cancelled(self):
  s=S.read_text(); self.assertIn('GATT_WRITE_TIMEOUT_MS',s)
  self.assertIn('source != activeWriteGatt || source != gatt || activeWriteGeneration != writeGeneration',s)
  self.assertIn('main.removeCallbacks(writeTimeoutTask)',s)
 def test_loopback_listener_retries_without_replacing_running_owner(self):
  s=S.read_text(); self.assertIn('if (apiServer != null && apiServer.isRunning())',s)
  self.assertIn('API_RETRY_MS',s); self.assertIn('candidate.close()',s)
 def test_active_polling_is_charge_only_and_uses_freshness_separate_from_saved_interval(self):
  s=S.read_text(); self.assertIn('activeFreshnessMillis()',s)
  self.assertIn('ACTIVE_CHARGE_INTERVAL_MS',s)
  self.assertIn('requestCharge(false); // ChargeState only',s)
 def test_gatt_stale_callbacks_do_not_clear_current_session_write(self):
  s=S.read_text(); self.assertIn('if (source != activeWriteGatt || source != gatt || activeWriteGeneration != writeGeneration) return;',s)
if __name__=='__main__':unittest.main()
