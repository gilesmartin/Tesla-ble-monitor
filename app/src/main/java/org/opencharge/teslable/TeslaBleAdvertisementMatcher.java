package org.opencharge.teslable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** VIN-bound matcher for the advertisement-name schemes supported by TeslaBLE. */
public final class TeslaBleAdvertisementMatcher {
    private TeslaBleAdvertisementMatcher() { }

    public static String expectedAdvertisement(String vin) {
        if (vin == null || vin.length() != 17) return "";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(vin.getBytes(StandardCharsets.US_ASCII));
            StringBuilder result = new StringBuilder("S");
            for (int i = 0; i < 8; i++) result.append(String.format(Locale.US, "%02x", digest[i] & 0xff));
            return result.append('C').toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to derive Tesla BLE advertisement name", e);
        }
    }

    public static boolean matchesVin(String deviceName, String vin) {
        if (deviceName == null || vin == null || vin.length() != 17) return false;
        String expected = expectedAdvertisement(vin);
        if (expected.isEmpty()) return false;

        // Legacy beacon names share the VIN-derived prefix; C/R/D/P identify the beacon.
        if (deviceName.length() == 18 && deviceName.regionMatches(0, expected, 0, 17)) {
            char suffix = Character.toUpperCase(deviceName.charAt(17));
            if ("CDRP".indexOf(suffix) >= 0) return true;
        }

        // Newer vehicles advertise "Tesla " followed by the VIN's last six characters.
        return ("Tesla " + vin.substring(11)).equals(deviceName);
    }

    /** True when the VIN hash matches but the legacy suffix is not supported by upstream. */
    public static boolean hasUnknownHashSuffix(String deviceName, String vin) {
        if (deviceName == null || vin == null || vin.length() != 17 || deviceName.length() != 18) return false;
        String expected = expectedAdvertisement(vin);
        if (expected.isEmpty() || !deviceName.regionMatches(0, expected, 0, 17)) return false;
        char suffix = Character.toUpperCase(deviceName.charAt(17));
        return "CDRP".indexOf(suffix) < 0;
    }

    /** Redacted type counter only; the caller must not log or retain the name itself. */
    public static boolean hasTeslaPrefix(String deviceName) {
        return deviceName != null && deviceName.startsWith("Tesla ");
    }
}
