package org.opencharge.teslable;

/** Chooses scan duty cycle without changing reconnect or vehicle-connection behavior. */
public final class BleScanPolicy {
    private BleScanPolicy() { }

    /** Explicit user scans are short, high-duty discovery; background reconnect remains low power. */
    public static boolean isHighDutyUserScan(boolean autoConnectAfterScan) {
        return !autoConnectAfterScan;
    }
}
