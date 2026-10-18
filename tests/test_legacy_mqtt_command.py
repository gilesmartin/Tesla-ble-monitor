import shutil, subprocess, tempfile, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/"app/src/main/java/org/opencharge/teslable/LegacyMqttCommand.java"
HARNESS=ROOT/"tests/java/LegacyMqttCommandHarness.java"
import os
JAVAC=shutil.which("javac") or str(Path(os.environ.get("JAVA_HOME", ""))/"bin"/"javac")
JAVA=shutil.which("java") or str(Path(os.environ.get("JAVA_HOME", ""))/"bin"/"java")
class LegacyMqttCommandTests(unittest.TestCase):
 def test_command_grammar_and_safety_contract(self):
  with tempfile.TemporaryDirectory() as out:
   built=subprocess.run([JAVAC,"-encoding","UTF-8","-d",out,str(SOURCE),str(HARNESS)],cwd=ROOT,text=True,capture_output=True)
   self.assertEqual(built.returncode,0,built.stdout+built.stderr)
   run=subprocess.run([JAVA,"-cp",out,"org.opencharge.teslable.LegacyMqttCommandHarness"],cwd=ROOT,text=True,capture_output=True,timeout=20)
   self.assertEqual(run.returncode,0,run.stdout+run.stderr)
if __name__=="__main__": unittest.main()
