import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class LegacyServiceWiringTests(unittest.TestCase):
 def test_legacy_mode_excludes_the_separate_generic_sensor_catalog(self):
  s=(ROOT/'app/src/main/java/org/opencharge/teslable/TeslaBleService.java').read_text()
  start=s.index('if (settings.isLegacyCompatibilityEnabled())')
  end=s.index('main.postDelayed(mqttFreshnessTask',start)
  branch=s[start:end]
  self.assertIn('legacyMqttPublisher.start();',branch)
  self.assertIn('} else {',branch)
  legacy,generic=branch.split('} else {',1)
  self.assertNotIn('new MqttBridgePublisher',legacy)
  self.assertIn('new MqttBridgePublisher',generic)
 def test_existing_ble_owner_consumes_opt_in_and_native_callbacks(self):
  s=(ROOT/'app/src/main/java/org/opencharge/teslable/TeslaBleService.java').read_text()
  for required in ['settings.isLegacyCompatibilityEnabled()', 'new LegacyMqttPublisher(', 'new LegacyMqttPublisher.Owner()', 'tesla.executeLegacy(', 'tesla.readLegacyState(', 'onCommandResult(String json)', 'onLegacyTelemetry(String json)', 'LegacyMqttStateMapper.map(', 'legacyMqttVin.equals(nativeVin)']:
   self.assertIn(required,s)
