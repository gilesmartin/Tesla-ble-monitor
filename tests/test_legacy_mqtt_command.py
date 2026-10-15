import shutil, subprocess, tempfile, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"app/src/main/java/org/opencharge/teslable/LegacyMqttCommand.java"
HARNESS=ROOT/"tests/java/LegacyMqttCommandHarness.java"
JDK=Path("/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin")
class LegacyMqttCommandTests(unittest.TestCase):
 def test_command_grammar_and_safety_contract(self):
  with tempfile.TemporaryDirectory() as out:
   built=subprocess.run([str(JDK/"javac"),"-encoding","UTF-8","-d",out,str(JAVA),str(HARNESS)],cwd=ROOT,text=True,capture_output=True)
   self.assertEqual(built.returncode,0,built.stdout+built.stderr)
   run=subprocess.run([str(JDK/"java"),"-cp",out,"org.opencharge.teslable.LegacyMqttCommandHarness"],cwd=ROOT,text=True,capture_output=True,timeout=20)
   self.assertEqual(run.returncode,0,run.stdout+run.stderr)
if __name__=="__main__": unittest.main()
