package org.opencharge.teslable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Read-only Home Assistant discovery definitions for documented fresh telemetry fields. */
public final class MqttDiscoveryCatalog {
    private static final String DISCOVERY_PREFIX = "homeassistant";
    private static final String STATE_PREFIX = "tesla_ble_monitor";
    private static final String AVAILABILITY_ONLINE = "online";
    private static final String AVAILABILITY_OFFLINE = "offline";
    private static final Pattern DEVICE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private MqttDiscoveryCatalog() { }

    public static List<Definition> create(String deviceId) {
        if (deviceId == null || !DEVICE_ID.matcher(deviceId).matches()) {
            throw new IllegalArgumentException("invalid MQTT device identifier");
        }
        String availability = availabilityTopic(deviceId);
        List<Definition> definitions = new ArrayList<>();
        definitions.add(new Definition("sensor", deviceId, "battery_percent", "Battery",
                "%", "battery", "measurement", null, null, availability));
        definitions.add(new Definition("sensor", deviceId, "usable_battery_percent", "Usable battery",
                "%", "battery", "measurement", null, null, availability));
        definitions.add(new Definition("sensor", deviceId, "range_km", "Estimated range",
                "km", "distance", "measurement", null, null, availability));
        definitions.add(new Definition("binary_sensor", deviceId, "charging", "Charging",
                null, "battery_charging", null, "true", "false", availability));
        return Collections.unmodifiableList(definitions);
    }

    public static String availabilityTopic(String deviceId) {
        if (deviceId == null || !DEVICE_ID.matcher(deviceId).matches()) {
            throw new IllegalArgumentException("invalid MQTT device identifier");
        }
        return STATE_PREFIX + "/" + deviceId + "/availability";
    }

    public static final class Definition {
        private final String component;
        private final String deviceId;
        private final String objectId;
        private final String name;
        private final String unit;
        private final String deviceClass;
        private final String stateClass;
        private final String payloadOn;
        private final String payloadOff;
        private final String availabilityTopic;

        private Definition(String component, String deviceId, String objectId, String name,
                String unit, String deviceClass, String stateClass,
                String payloadOn, String payloadOff, String availabilityTopic) {
            this.component = component;
            this.deviceId = deviceId;
            this.objectId = objectId;
            this.name = name;
            this.unit = unit;
            this.deviceClass = deviceClass;
            this.stateClass = stateClass;
            this.payloadOn = payloadOn;
            this.payloadOff = payloadOff;
            this.availabilityTopic = availabilityTopic;
        }

        public String getDiscoveryTopic() {
            return DISCOVERY_PREFIX + "/" + component + "/" + deviceId + "/" + objectId + "/config";
        }

        public String getStateTopic() {
            return STATE_PREFIX + "/" + deviceId + "/" + objectId;
        }

        public String getUniqueId() { return deviceId + "_" + objectId; }
        public String getObjectId() { return objectId; }
        public String getAvailabilityTopic() { return availabilityTopic; }

        public String toJson() {
            StringBuilder json = new StringBuilder(512);
            json.append('{');
            field(json, "name", name, false);
            field(json, "unique_id", getUniqueId(), true);
            field(json, "state_topic", getStateTopic(), true);
            field(json, "availability_topic", availabilityTopic, true);
            field(json, "payload_available", AVAILABILITY_ONLINE, true);
            field(json, "payload_not_available", AVAILABILITY_OFFLINE, true);
            if (unit != null) field(json, "unit_of_measurement", unit, true);
            if (deviceClass != null) field(json, "device_class", deviceClass, true);
            if (stateClass != null) field(json, "state_class", stateClass, true);
            if (payloadOn != null) field(json, "payload_on", payloadOn, true);
            if (payloadOff != null) field(json, "payload_off", payloadOff, true);
            json.append(",\"device\":{\"identifiers\":[");
            appendQuoted(json, deviceId);
            json.append("],\"name\":\"Tesla BLE Monitor\",\"manufacturer\":\"Opencharge\",\"model\":\"BLE telemetry bridge\"}}");
            return json.toString();
        }

        private static void field(StringBuilder json, String key, String value, boolean comma) {
            if (comma) json.append(',');
            appendQuoted(json, key);
            json.append(':');
            appendQuoted(json, value);
        }

        private static void appendQuoted(StringBuilder json, String value) {
            json.append('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"': json.append("\\\""); break;
                    case '\\': json.append("\\\\"); break;
                    case '\b': json.append("\\b"); break;
                    case '\f': json.append("\\f"); break;
                    case '\n': json.append("\\n"); break;
                    case '\r': json.append("\\r"); break;
                    case '\t': json.append("\\t"); break;
                    default:
                        if (c < 0x20) {
                            String hex = Integer.toHexString(c);
                            json.append("\\u");
                            for (int pad = hex.length(); pad < 4; pad++) json.append('0');
                            json.append(hex);
                        } else json.append(c);
                }
            }
            json.append('"');
        }
    }
}
