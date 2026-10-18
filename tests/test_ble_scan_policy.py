"""Tests for the distinction between explicit discovery and reconnect scans."""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / "app/src/main/java/org/opencharge/teslable/BleScanPolicy.java"


class BleScanPolicyTest(unittest.TestCase):
    def test_user_scan_uses_high_duty_but_reconnect_scan_does_not(self):
        self.assertTrue(POLICY.exists(), "BLE scan policy is not implemented yet")
        self.assertIsNotNone(shutil.which("javac"))
        self.assertIsNotNone(shutil.which("java"))
        harness = """import org.opencharge.teslable.BleScanPolicy;
public final class BleScanPolicyHarness {
    public static void main(String[] args) {
        System.out.print(BleScanPolicy.isHighDutyUserScan(false) + "," +
                BleScanPolicy.isHighDutyUserScan(true));
    }
}
"""
        with tempfile.TemporaryDirectory() as temp:
            hp = Path(temp) / "BleScanPolicyHarness.java"
            hp.write_text(harness, encoding="utf-8")
            subprocess.run(["javac", "-d", temp, str(POLICY), str(hp)], check=True,
                           capture_output=True, text=True)
            result = subprocess.run(["java", "-cp", temp, "BleScanPolicyHarness"],
                                    check=True, capture_output=True, text=True)
        self.assertEqual("true,false", result.stdout)


if __name__ == "__main__":
    unittest.main()
