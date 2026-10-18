import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class ControllerEnrollmentTests(unittest.TestCase):
 def test_manual_driver_enrollment_stays_local_and_mqtt_deploy_reuses_existing_key(self):
  j=ROOT/'app/src/main/java/org/opencharge/teslable'
  ui=(j/'MainActivity.java').read_text();service=(j/'TeslaBleService.java').read_text();native=(ROOT/'app/src/main/cpp/native_bridge.cpp').read_text();extra=(ROOT/'app/src/main/cpp/legacy_extra_command.h').read_text()
  self.assertIn('new android.app.AlertDialog.Builder(this)',ui)
  self.assertIn('Vehicle parked — request approval',ui)
  self.assertIn('requestControllerEnrollment(confirmedVin)',ui)
  self.assertIn('public boolean requestControllerEnrollment(String confirmedVin)',service)
  mqtt=service[service.index('private void reconfigureMqttPublisher()'):service.index('private void updateMqttTelemetry()')]
  self.assertNotIn('requestControllerEnrollment',mqtt)
  self.assertNotIn('pairExistingControllerKey',(j/'LegacyMqttPublisher.java').read_text())
  marker='Java_org_opencharge_teslable_NativeTesla_pairExistingControllerKeyOnOwner'
  self.assertIn(marker,native)
  body=native[native.index(marker):];body=body[:body.index('extern "C"',10)]
  self.assertLess(body.index('!vehicle->has_private_key()'),body.index('vehicle->pair(Keys_Role_ROLE_DRIVER'))
  self.assertIn('vehicle.has_private_key()',extra)
  self.assertIn('vehicle.pair(Keys_Role_ROLE_OWNER',extra)
  self.assertNotIn('regenerate_key',body)
