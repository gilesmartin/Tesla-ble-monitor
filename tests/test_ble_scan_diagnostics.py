"""Privacy-safe BLE scan diagnostic contract and helper behavior tests."""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / "app/src/main/java/org/opencharge/teslable/BleScanDiagnostics.java"
SERVICE = ROOT / "app/src/main/java/org/opencharge/teslable/TeslaBleService.java"


class BleScanDiagnosticsTest(unittest.TestCase):
    def test_aggregate_helper_counts_redacted_categories(self):
        self.assertTrue(HELPER.exists(), "BLE scan diagnostic helper is not implemented yet")
        self.assertIsNotNone(shutil.which("javac"), "javac is required for this helper test")
        self.assertIsNotNone(shutil.which("java"), "java is required for this helper test")
        probe = subprocess.run(["javac", "-version"], capture_output=True, text=True)
        if probe.returncode and "Unable to locate a Java Runtime" in probe.stderr:
            self.skipTest("Mac CommandLineTools Java shim is present but no JDK runtime is installed")
        self.assertEqual(probe.returncode, 0, probe.stderr)
        harness = """import org.opencharge.teslable.BleScanDiagnostics;
public final class BleScanDiagnosticsHarness {
    public static void main(String[] args) {
        BleScanDiagnostics d = new BleScanDiagnostics();
        d.recordResult(true, true, false, false, false);
        d.recordResult(false, false, true, true, true);
        d.recordResult(false, false, true, false, false);
        System.out.print(d.summary());
    }
}
"""
        with tempfile.TemporaryDirectory() as temp:
            harness_path = Path(temp) / "BleScanDiagnosticsHarness.java"
            harness_path.write_text(harness, encoding="utf-8")
            subprocess.run(
                ["javac", "-d", temp, str(HELPER), str(harness_path)],
                check=True, capture_output=True, text=True,
            )
            result = subprocess.run(
                ["java", "-cp", temp, "BleScanDiagnosticsHarness"],
                check=True, capture_output=True, text=True,
            )
        self.assertEqual(
            "BLE scan diagnostics: result_callbacks=3 scan_record_name_present=1 "
            "scan_record_name_missing=2 cached_name_checked=2 cached_name_present=1 "
            "scan_record_exact_match=1 cached_name_exact_match=1",
            result.stdout,
        )

    def test_service_reports_redacted_summary_and_observes_cached_name(self):
        service = SERVICE.read_text(encoding="utf-8")
        self.assertIn("scanDiagnostics.recordResult(", service)
        self.assertIn("scanDiagnostics.summary()", service)
        self.assertIn("device.getName()", service)
        self.assertIn("scanDiagnostics = new BleScanDiagnostics()", service)

    def test_diagnostic_does_not_persist_ble_addresses_or_raw_payloads(self):
        service = SERVICE.read_text(encoding="utf-8")
        self.assertNotIn('putString("last_ble_address"', service, "scan diagnostics must not persist a BLE address")
        self.assertNotIn("getScanRecord().getBytes()", service, "scan diagnostics must not read raw advertisement bytes")


if __name__ == "__main__":
    unittest.main()
