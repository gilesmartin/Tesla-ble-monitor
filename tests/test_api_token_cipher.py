import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CIPHER = ROOT / "app/src/main/java/org/opencharge/teslable/ApiTokenCipher.java"
HARNESS = ROOT / "tests/java/ApiTokenCipherHarness.java"


def java_tool(name):
    home = os.environ.get("JAVA_HOME")
    candidates = [Path(home) / "bin" / name] if home else []
    candidates += [Path("/opt/homebrew/opt/openjdk/bin") / name,
                   Path("/usr/local/opt/openjdk/bin") / name]
    found = shutil.which(name)
    if found:
        candidates.append(Path(found))
    return next((p for p in candidates if p.is_file() and os.access(p, os.X_OK)), None)


class ApiTokenCipherTests(unittest.TestCase):
    def test_token_is_encrypted_and_authenticated(self):
        javac, java = java_tool("javac"), java_tool("java")
        if not javac or not java:
            self.fail("JDK tools required for AES-GCM token test")
        with tempfile.TemporaryDirectory(prefix="tesla-token-test-") as output:
            result = subprocess.run([str(javac), "-encoding", "UTF-8", "-d", output,
                                     str(CIPHER), str(HARNESS)],
                                    cwd=ROOT, text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            result = subprocess.run([str(java), "-cp", output,
                                     "org.opencharge.teslable.ApiTokenCipherHarness"],
                                    cwd=ROOT, text=True, capture_output=True, timeout=20)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("PASS", result.stdout)


if __name__ == "__main__":
    unittest.main()
