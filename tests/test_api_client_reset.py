import os
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

class ApiClientResetTests(unittest.TestCase):
    def _run(self, mode):
        home = Path(os.environ["JAVA_HOME"]) / "bin"
        with tempfile.TemporaryDirectory(prefix="api-reset-test-") as output:
            compile_result = subprocess.run([str(home / "javac"), "-encoding", "UTF-8", "-d", output, str(ROOT / "app/src/main/java/org/opencharge/teslable/TelemetryHttpServer.java"), str(ROOT / "tests/java/TelemetryHttpServerResilienceHarness.java")], text=True, capture_output=True)
            self.assertEqual(compile_result.returncode, 0, compile_result.stdout + compile_result.stderr)
            result = subprocess.run([str(home / "java"), "-cp", output, "org.opencharge.teslable.TelemetryHttpServerResilienceHarness", mode], text=True, capture_output=True, timeout=12)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("PASS", result.stdout)

    def test_client_reset_does_not_stop_real_api_listener(self):
        self._run("reset")

    def test_old_api_worker_cannot_consume_restarted_listener(self):
        self._run("lifecycle")

if __name__ == "__main__":
    unittest.main()
