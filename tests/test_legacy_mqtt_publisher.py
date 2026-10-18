import unittest,subprocess,tempfile,os
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class LegacyMqttPublisherTests(unittest.TestCase):
 def run_mode(self,mode):
  with tempfile.TemporaryDirectory() as out:
   package=ROOT/'app/src/main/java/org/opencharge/teslable'
   sources=[package/(n+'.java') for n in ['MqttBridgeConfig','MqttDiscoveryCatalog','MqttBridgePublisher','LegacyMqttCommand','LegacyMqttDiscoveryCatalog','LegacyMqttPublisher']]+[ROOT/'tests/java/LegacyMqttPublisherHarness.java']
   subprocess.run(['javac','-d',out,*map(str,sources)],check=True,capture_output=True,text=True)
   result=subprocess.run(['java','-cp',out,'org.opencharge.teslable.LegacyMqttPublisherHarness',mode],capture_output=True,text=True)
   self.assertEqual(result.returncode,0,result.stdout+result.stderr)
 def test_commands_are_serialized_on_owner_with_replay_guards(self):self.run_mode('commands')
 def test_section_freshness_and_birth_fail_closed(self):self.run_mode('freshness')
 def test_policy_and_key_commands_are_explicit_and_gated(self):self.run_mode('policy')
