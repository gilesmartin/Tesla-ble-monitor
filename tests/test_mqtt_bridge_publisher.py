import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = [
    ROOT / "app/src/main/java/org/opencharge/teslable/MqttBridgeConfig.java",
    ROOT / "app/src/main/java/org/opencharge/teslable/MqttDiscoveryCatalog.java",
    ROOT / "app/src/main/java/org/opencharge/teslable/MqttBridgePublisher.java",
    ROOT / "tests/java/MqttBridgePublisherHarness.java",
]


class MqttBridgePublisherTests(unittest.TestCase):
    def run_scenario(self, scenario):
        javac, java = shutil.which("javac"), shutil.which("java")
        self.assertIsNotNone(javac, "JDK javac is required for publisher tests")
        self.assertIsNotNone(java, "JDK java is required for publisher tests")
        with tempfile.TemporaryDirectory(prefix="tesla-mqtt-publisher-") as output:
            compiled = subprocess.run(
                [javac, "-encoding", "UTF-8", "-d", output, *map(str, SOURCES)],
                cwd=ROOT, text=True, capture_output=True)
            self.assertEqual(compiled.returncode, 0, compiled.stdout + compiled.stderr)
            result = subprocess.run(
                [java, "-cp", output, "org.opencharge.teslable.MqttBridgePublisherHarness", scenario],
                cwd=ROOT, text=True, capture_output=True, timeout=20)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("PASS " + scenario, result.stdout)

    def test_fresh_data_publishes_only_read_only_retained_state(self):
        self.run_scenario("fresh")

    def test_stale_data_marks_unavailable_without_republishing_states(self):
        self.run_scenario("stale")

    def test_home_assistant_birth_republishes_discovery(self):
        self.run_scenario("birth")


if __name__ == "__main__":
    unittest.main()
