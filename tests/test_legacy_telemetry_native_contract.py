import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class LegacyTelemetryNativeContractTests(unittest.TestCase):
 def test_internal_sections_are_separate_from_unchanged_schema1_telemetry(self):
  source=(ROOT/'app/src/main/cpp/native_bridge.cpp').read_text()
  self.assertIn('onNativeLegacyTelemetry',source)
  for name in ['climate','drive','tire_pressure','closures']:
   self.assertIn('set_'+name+'_state_callback',source)
  self.assertIn('NativeTesla_readLegacyStateOnOwner',source)
  self.assertIn('TeslaBLE::WakePolicy::NO_WAKE_FAIL',source)
