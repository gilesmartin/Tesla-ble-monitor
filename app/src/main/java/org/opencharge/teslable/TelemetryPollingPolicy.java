package org.opencharge.teslable;

/** Pure acquisition decisions. A consumer lease is not evidence the vehicle is awake. */
final class TelemetryPollingPolicy {
    private TelemetryPollingPolicy() { }
    static boolean recent(long age, long limit) { return age >= 0 && age <= limit; }
    static boolean driving(String gear, Double speed) {
        return "D".equals(gear) || "R".equals(gear)
                || ((gear == null || gear.isEmpty()) && speed != null && Double.isFinite(speed) && speed > 0 && speed <= 200);
    }
    static String mode(boolean asleep, boolean lease, long driveAge, String gear,
                       Double speed, long chargeAge, Boolean charging) {
        return mode(asleep, lease, driveAge, gear, speed, chargeAge, charging, false);
    }
    static String mode(boolean asleep, boolean lease, long driveAge, String gear,
                       Double speed, long chargeAge, Boolean charging, boolean occupied) {
        if (asleep) return "sleeping";
        boolean chargingNow = Boolean.TRUE.equals(charging) && recent(chargeAge, 45_000L);
        if (lease && recent(driveAge, 1_500L) && driving(gear, speed) && !chargingNow) return "driving";
        if (chargingNow) return "charging";
        if (occupied) return "occupied";
        return "parked";
    }
    static long interval(String mode, int savedSeconds) {
        if ("driving".equals(mode)) return 10_000L;
        if ("charging".equals(mode)) return 15_000L;
        if ("occupied".equals(mode)) return 30_000L;
        return Math.max(120_000L, savedSeconds * 1_000L);
    }
    static long freshness(String mode) {
        if ("driving".equals(mode)) return 30_000L;
        if ("charging".equals(mode)) return 45_000L;
        if ("occupied".equals(mode)) return 90_000L;
        return 300_000L;
    }
    static long retryDelay(long interval, int failures) {
        return failures <= 0 ? interval : Math.min(120_000L, interval * (1L << Math.min(failures, 4)));
    }
    static boolean driveProbe(boolean asleep, boolean initialProbe, boolean occupied,
                              long driveAge, String gear, Double speed) {
        if (asleep) return false;
        if (initialProbe || occupied) return true;
        if ("P".equals(gear) || "N".equals(gear)) return false;
        return recent(driveAge, 15_000L) && driving(gear, speed);
    }
}
