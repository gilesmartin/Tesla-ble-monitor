import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class LegacyJniContractTests(unittest.TestCase):
 def test_commands_use_existing_owner_and_a_distinct_ack_callback(self):
  native=(ROOT/'app/src/main/cpp/native_bridge.cpp').read_text()
  java=(ROOT/'app/src/main/java/org/opencharge/teslable/NativeTesla.java').read_text()
  self.assertIn('NativeTesla_executeLegacy',native)
  self.assertIn('dispatch_legacy_command(*vehicle',native)
  self.assertIn('onNativeCommandResult',native)
  self.assertIn('op!="generate-keys"&&!vehicle->is_connected()',native)
  self.assertIn('REGENERATE_KEY',(ROOT/'app/src/main/cpp/legacy_extra_command.h').read_text())
  self.assertIn('public boolean executeLegacy(',java)
  self.assertIn('Looper.myLooper() != android.os.Looper.getMainLooper()',java)
  self.assertIn('default void onCommandResult(String json)',java)
  self.assertNotIn('Runtime.getRuntime()',java)
