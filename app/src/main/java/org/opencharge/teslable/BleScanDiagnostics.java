package org.opencharge.teslable;

/** Aggregates BLE scan diagnostics without retaining device identifiers or advertisement data. */
public final class BleScanDiagnostics {
    private static final int MAX_COUNT = 1_000_000;
    private int resultCallbacks;
    private int scanRecordNamePresent;
    private int scanRecordNameMissing;
    private int cachedNameChecked;
    private int cachedNamePresent;
    private int scanRecordExactMatch;
    private int cachedNameExactMatch;

    public void recordResult(boolean recordNamePresent, boolean recordNameMatches,
            boolean cachedNameWasChecked, boolean cachedNameIsPresent, boolean cachedNameMatches) {
        resultCallbacks = increment(resultCallbacks);
        if (recordNamePresent) scanRecordNamePresent = increment(scanRecordNamePresent);
        else scanRecordNameMissing = increment(scanRecordNameMissing);
        if (recordNamePresent && recordNameMatches) scanRecordExactMatch = increment(scanRecordExactMatch);
        if (cachedNameWasChecked) {
            cachedNameChecked = increment(cachedNameChecked);
            if (cachedNameIsPresent) cachedNamePresent = increment(cachedNamePresent);
            if (cachedNameIsPresent && cachedNameMatches) cachedNameExactMatch = increment(cachedNameExactMatch);
        }
    }

    private static int increment(int count) {
        return count >= MAX_COUNT ? MAX_COUNT : count + 1;
    }

    public String summary() {
        return "BLE scan diagnostics: result_callbacks=" + resultCallbacks
                + " scan_record_name_present=" + scanRecordNamePresent
                + " scan_record_name_missing=" + scanRecordNameMissing
                + " cached_name_checked=" + cachedNameChecked
                + " cached_name_present=" + cachedNamePresent
                + " scan_record_exact_match=" + scanRecordExactMatch
                + " cached_name_exact_match=" + cachedNameExactMatch;
    }
}
