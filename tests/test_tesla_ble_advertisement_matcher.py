"""Regression tests for the VIN/name matching behavior used by TeslaBLE."""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MATCHER = ROOT / "app/src/main/java/org/opencharge/teslable/TeslaBleAdvertisementMatcher.java"


class TeslaBleAdvertisementMatcherTest(unittest.TestCase):
    def test_matches_all_upstream_supported_name_schemes(self):
        self.assertTrue(MATCHER.is_file(), "production VIN matcher is missing")
        javac = shutil.which("javac")
        self.assertIsNotNone(javac, "javac is required for the production matcher test")
        harness = r"""import org.opencharge.teslable.TeslaBleAdvertisementMatcher;
public final class TeslaBleAdvertisementMatcherHarness {
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        String vin = "5YJS0000000000000";
        String expected = "S1a87a5a75f3df858";
        check(TeslaBleAdvertisementMatcher.expectedAdvertisement(vin).equals(expected + "C"), "canonical hash vector");
        for (String suffix : new String[] {"C", "R", "D", "P"}) {
            check(TeslaBleAdvertisementMatcher.matchesVin(expected + suffix, vin), "old scheme suffix " + suffix);
        }
        check(TeslaBleAdvertisementMatcher.matchesVin(expected + "r", vin), "case-insensitive beacon suffix");
        check(TeslaBleAdvertisementMatcher.matchesVin("Tesla " + vin.substring(11), vin), "new Tesla plus last-six scheme");
        check(!TeslaBleAdvertisementMatcher.matchesVin(expected + "X", vin), "unknown beacon suffix rejected");
        check(!TeslaBleAdvertisementMatcher.matchesVin(expected + "C", "5YJS0000000000001"), "different VIN rejected");
        check(!TeslaBleAdvertisementMatcher.matchesVin("Tesla 000001", vin), "different last-six rejected");
        check(!TeslaBleAdvertisementMatcher.matchesVin(expected + "C", "bad"), "invalid VIN length rejected");
        check(!TeslaBleAdvertisementMatcher.matchesVin(null, vin), "null name rejected");
        check(!TeslaBleAdvertisementMatcher.matchesVin(expected + "C", null), "null VIN rejected");
        check(TeslaBleAdvertisementMatcher.hasUnknownHashSuffix(expected + "X", vin), "unknown suffix candidate detected");
        check(!TeslaBleAdvertisementMatcher.hasUnknownHashSuffix(expected + "R", vin), "supported suffix not classified unknown");
        check(!TeslaBleAdvertisementMatcher.hasUnknownHashSuffix("S0000000000000000X", vin), "wrong hash prefix rejected");
        check(TeslaBleAdvertisementMatcher.hasTeslaPrefix("Tesla 000000"), "Tesla label classified");
        check(!TeslaBleAdvertisementMatcher.hasTeslaPrefix("Other 000000"), "other label rejected");
        check(TeslaBleAdvertisementMatcher.shortVinFingerprint(vin).equals("1a87a5a7"), "short derived VIN fingerprint");
        check(TeslaBleAdvertisementMatcher.shortVinFingerprint("bad").isEmpty(), "invalid VIN has no fingerprint");
        System.out.print("matcher cases passed");
    }
}
"""
        with tempfile.TemporaryDirectory() as temp:
            harness_path = Path(temp) / "TeslaBleAdvertisementMatcherHarness.java"
            harness_path.write_text(harness, encoding="utf-8")
            compile_result = subprocess.run(
                [javac, "-d", temp, str(MATCHER), str(harness_path)], capture_output=True, text=True
            )
            self.assertEqual(compile_result.returncode, 0, compile_result.stderr)
            result = subprocess.run(
                [shutil.which("java"), "-cp", temp, "TeslaBleAdvertisementMatcherHarness"],
                capture_output=True, text=True,
            )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "matcher cases passed")


    def test_scan_path_uses_vin_matcher_for_advertised_or_cached_name(self):
        service = (ROOT / "app/src/main/java/org/opencharge/teslable/TeslaBleService.java").read_text(encoding="utf-8")
        self.assertIn("String name = scanRecordNamePresent ? scanRecordName : cachedName;", service)
        self.assertIn("TeslaBleAdvertisementMatcher.matchesVin(name, currentVin)", service)
        self.assertIn("return TeslaBleAdvertisementMatcher.expectedAdvertisement(vin);", service)


    def test_status_exposes_only_a_short_target_fingerprint(self):
        service = (ROOT / "app/src/main/java/org/opencharge/teslable/TeslaBleService.java").read_text(encoding="utf-8")
        self.assertIn('response.put("target_vin_fingerprint", TeslaBleAdvertisementMatcher.shortVinFingerprint(currentVin));', service)


if __name__ == "__main__":
    unittest.main()
