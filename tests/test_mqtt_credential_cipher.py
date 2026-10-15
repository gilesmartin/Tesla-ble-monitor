import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CIPHER = ROOT / "app/src/main/java/org/opencharge/teslable/MqttCredentialCipher.java"
HARNESS = ROOT / "tests/java/MqttCredentialCipherHarness.java"


class MqttCredentialCipherTests(unittest.TestCase):
    def test_envelope_encrypts_and_authenticates_mqtt_credentials(self):
        javac, java = shutil.which("javac"), shutil.which("java")
        self.assertIsNotNone(javac, "JDK javac is required for credential cipher tests")
        self.assertIsNotNone(java, "JDK java is required for credential cipher tests")
        with tempfile.TemporaryDirectory(prefix="tesla-mqtt-cipher-") as output:
            compiled = subprocess.run(
                [javac, "-encoding", "UTF-8", "-d", output, str(CIPHER), str(HARNESS)],
                cwd=ROOT, text=True, capture_output=True)
            self.assertEqual(compiled.returncode, 0, compiled.stdout + compiled.stderr)
            result = subprocess.run(
                [java, "-cp", output, "org.opencharge.teslable.MqttCredentialCipherHarness"],
                cwd=ROOT, text=True, capture_output=True, timeout=20)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("PASS", result.stdout)


if __name__ == "__main__":
    unittest.main()
