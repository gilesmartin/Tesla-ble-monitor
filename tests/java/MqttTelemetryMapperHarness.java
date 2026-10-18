package org.opencharge.teslable;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MqttTelemetryMapperHarness {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        if (args.length > 0 && "stale".equals(args[0])) {
            Map<String, String> actual = MqttTelemetryMapper.toStates(
                    true, false, 71.0, 68.5, 301.25, true);
            check(actual.isEmpty(), "stale telemetry must not produce current MQTT states");
            System.out.println("MqttTelemetryMapperHarness: PASS stale telemetry suppression");
            return;
        }
        if (args.length > 0 && "invalid".equals(args[0])) {
            Map<String, String> actual = MqttTelemetryMapper.toStates(
                    true, true, 101.0, -1.0, Double.NaN, null);
            check(actual.isEmpty(), "out-of-range and non-finite measurements must not be published");
            System.out.println("MqttTelemetryMapperHarness: PASS invalid measurement suppression");
            return;
        }

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("battery_percent", "71.0");
        expected.put("usable_battery_percent", "68.5");
        expected.put("range_km", "301.25");
        expected.put("charging", "true");
        Map<String, String> actual = MqttTelemetryMapper.toStates(
                true, true, 71.0, 68.5, 301.25, true);
        check(expected.equals(actual), "fresh authenticated telemetry should map all four documented fields");
        System.out.println("MqttTelemetryMapperHarness: PASS fresh telemetry mapping");
    }
}
