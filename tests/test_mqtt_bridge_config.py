import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "app/src/main/java/org/opencharge/teslable/MqttBridgeConfig.java"
HARNESS = ROOT / "tests/java/MqttBridgeConfigHarness.java"


class MqttBridgeConfigTests(unittest.TestCase):
    def run_scenario(self, scenario):
        javac, java = shutil.which("javac"), shutil.which("java")
        self.assertIsNotNone(javac, "JDK javac is required for config tests")
        self.assertIsNotNone(java, "JDK java is required for config tests")
        with tempfile.TemporaryDirectory(prefix="tesla-mqtt-config-") as output:
            compiled = subprocess.run(
                [javac, "-encoding", "UTF-8", "-d", output, str(CONFIG), str(HARNESS)],
                cwd=ROOT, text=True, capture_output=True)
            self.assertEqual(compiled.returncode, 0, compiled.stdout + compiled.stderr)
            result = subprocess.run(
                [java, "-cp", output, "org.opencharge.teslable.MqttBridgeConfigHarness", scenario],
                cwd=ROOT, text=True, capture_output=True, timeout=20)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("PASS " + scenario, result.stdout)

    def test_tls_broker_is_accepted(self):
        self.run_scenario("tls")

    def test_plaintext_requires_explicit_opt_in(self):
        self.run_scenario("plain-denied")

    def test_plaintext_can_be_enabled_explicitly_for_legacy_lan_broker(self):
        self.run_scenario("plain-allowed")

    def test_credentials_cannot_be_embedded_in_uri(self):
        self.run_scenario("userinfo-denied")


if __name__ == "__main__":
    unittest.main()
