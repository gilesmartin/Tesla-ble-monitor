import pathlib,subprocess,tempfile,unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
class LegacyMqttStateMapperTests(unittest.TestCase):
 def test_legacy_native_values_map_to_exact_mqtt_fields(self):
  source=ROOT/'app/src/main/java/org/opencharge/teslable/LegacyMqttStateMapper.java'
  self.assertTrue(source.exists(),'Legacy state mapper is not implemented')
  with tempfile.TemporaryDirectory() as tmp:
   subprocess.run(['javac','-d',tmp,str(source),str(ROOT/'tests/java/LegacyMqttStateMapperHarness.java')],check=True,capture_output=True,text=True)
   p=subprocess.run(['java','-cp',tmp,'org.opencharge.teslable.LegacyMqttStateMapperHarness'],check=True,capture_output=True,text=True)
   self.assertIn('PASS',p.stdout)
