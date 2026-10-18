package org.opencharge.teslable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maps only the four fresh top-level measurements in the local telemetry API. */
public final class MqttTelemetryMapper {
    private MqttTelemetryMapper() { }

    public static Map<String, String> toStates(boolean available, boolean fresh,
            Double batteryPercent, Double usableBatteryPercent, Double rangeKm, Boolean charging) {
        if (!available || !fresh) return Collections.emptyMap();

        Map<String, String> states = new LinkedHashMap<>();
        putBounded(states, "battery_percent", batteryPercent, 0.0, 100.0);
        putBounded(states, "usable_battery_percent", usableBatteryPercent, 0.0, 100.0);
        putBounded(states, "range_km", rangeKm, 0.0, 65535.0);
        if (charging != null) states.put("charging", charging.toString());
        return Collections.unmodifiableMap(states);
    }

    private static void putBounded(Map<String, String> states, String key,
            Double value, double minimum, double maximum) {
        if (value == null || !Double.isFinite(value) || value < minimum || value > maximum) return;
        states.put(key, Double.toString(value));
    }
}
