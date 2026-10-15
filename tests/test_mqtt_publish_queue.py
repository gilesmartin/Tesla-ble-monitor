import unittest,subprocess,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class MqttPublishQueueTests(unittest.TestCase):
 def test_fifo_capacity_retries_generations_and_close(self):
  with tempfile.TemporaryDirectory() as out:
   r=subprocess.run(['javac','-d',out,str(ROOT/'app/src/main/java/org/opencharge/teslable/MqttPublishQueue.java'),str(ROOT/'tests/java/MqttPublishQueueHarness.java')],capture_output=True,text=True)
   self.assertEqual(r.returncode,0,r.stdout+r.stderr)
   r=subprocess.run(['java','-cp',out,'org.opencharge.teslable.MqttPublishQueueHarness'],capture_output=True,text=True)
   self.assertEqual(r.returncode,0,r.stdout+r.stderr)
