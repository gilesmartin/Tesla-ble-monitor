import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class LegacyServiceWiringTests(unittest.TestCase):
 def test_existing_ble_owner_consumes_opt_in_and_native_callbacks(self):
  s=(ROOT/'app/src/main/java/org/opencharge/teslable/TeslaBleService.java').read_text()
  for required in ['settings.isLegacyCompatibilityEnabled()', 'new LegacyMqttPublisher(', 'new LegacyMqttPublisher.Owner()', 'tesla.executeLegacy(', 'tesla.readLegacyState(', 'onCommandResult(String json)', 'onLegacyTelemetry(String json)', 'LegacyMqttStateMapper.map(', 'legacyMqttVin.equals(nativeVin)']:
   self.assertIn(required,s)
