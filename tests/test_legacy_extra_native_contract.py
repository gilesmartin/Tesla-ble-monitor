import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class LegacyExtraNativeContractTests(unittest.TestCase):
 def test_remaining_commands_have_validated_plans_before_native_dispatch(self):
  header=ROOT/'app/src/main/cpp/legacy_extra_command.h'
  self.assertTrue(header.exists(),'Extra command planner/dispatcher missing')
  s=header.read_text()
  self.assertIn('plan_legacy_extra',s)
  self.assertIn('CarServer_ScheduledChargingAction',s)
  self.assertIn('ROLE_', (ROOT/'app/src/main/cpp/native_bridge.cpp').read_text())
  self.assertIn('vehicle.has_private_key()',s)
  self.assertIn('vehicle.pair(Keys_Role_ROLE_OWNER',s)
  self.assertIn('vehicle.regenerate_key(callback)',s)
