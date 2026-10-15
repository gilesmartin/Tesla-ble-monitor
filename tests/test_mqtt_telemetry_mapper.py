import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAPPER = ROOT / "app/src/main/java/org/opencharge/teslable/MqttTelemetryMapper.java"
HARNESS = ROOT / "tests/java/MqttTelemetryMapperHarness.java"


def java_tool(name):
    home = os.environ.get("JAVA_HOME")
    candidates = [Path(home) / "bin" / name] if home else []
    found = shutil.which(name)
    if found:
        candidates.append(Path(found))
    return next((path for path in candidates if path.is_file() and os.access(path, os.X_OK)), None)


class MqttTelemetryMapperTests(unittest.TestCase):
    def run_harness(self, *args):
        javac, java = java_tool("javac"), java_tool("java")
        self.assertIsNotNone(javac, "JDK javac is required for the mapper test")
        self.assertIsNotNone(java, "JDK java is required for the mapper test")
        with tempfile.TemporaryDirectory(prefix="tesla-mqtt-mapper-") as output:
            compiled = subprocess.run(
                [str(javac), "-encoding", "UTF-8", "-d", output, str(MAPPER), str(HARNESS)],
                cwd=ROOT, text=True, capture_output=True)
            self.assertEqual(compiled.returncode, 0, compiled.stdout + compiled.stderr)
            result = subprocess.run(
                [str(java), "-cp", output, "org.opencharge.teslable.MqttTelemetryMapperHarness", *args],
                cwd=ROOT, text=True, capture_output=True, timeout=20)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("PASS", result.stdout)

    def test_fresh_authenticated_telemetry_maps_documented_fields(self):
        self.run_harness()

    def test_stale_telemetry_does_not_emit_current_states(self):
        self.run_harness("stale")

    def test_invalid_measurements_are_omitted(self):
        self.run_harness("invalid")


if __name__ == "__main__":
    unittest.main()
