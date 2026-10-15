package org.opencharge.teslable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only MQTT/HA publisher. It deliberately has no vehicle-command subscription. */
public final class MqttBridgePublisher {
    private static final String HA_BIRTH_TOPIC = "homeassistant/status";
    private static final String HA_BIRTH_ONLINE = "online";
    private static final String PAYLOAD_ONLINE = "online";
    private static final String PAYLOAD_OFFLINE = "offline";
    private final String brokerUri;
    private final boolean allowPlaintext;
    private final String username;
    private final String password;
    private final String deviceId;
    private final String availabilityTopic;
    private final String clientId;
    private final Transport transport;
    private final List<MqttDiscoveryCatalog.Definition> definitions;
    private Map<String, String> lastStates = Collections.emptyMap();
    private boolean telemetryFresh;
    private boolean connected;
    private boolean started;
    private boolean closed;

    public MqttBridgePublisher(String brokerUri, boolean allowPlaintext, String username,
            String password, String deviceId, Transport transport) {
        MqttBridgeConfig.validateBrokerUri(brokerUri, allowPlaintext);
        MqttBridgeConfig.validateCredentials(username, password);
        if (transport == null) throw new IllegalArgumentException("MQTT transport is required");
        this.brokerUri = brokerUri.trim();
        this.allowPlaintext = allowPlaintext;
        this.username = username;
        this.password = password;
        this.deviceId = deviceId;
        this.transport = transport;
        this.definitions = MqttDiscoveryCatalog.create(deviceId);
        this.availabilityTopic = MqttDiscoveryCatalog.availabilityTopic(deviceId);
        this.clientId = "tbm_" + deviceId.substring(0, Math.min(16, deviceId.length()));
    }

    public synchronized void start() {
        if (started || closed) return;
        started = true;
        try {
            transport.connect(brokerUri, clientId, username, password.toCharArray(),
                    availabilityTopic, PAYLOAD_OFFLINE, new Transport.Listener() {
                        @Override public void onConnected() { MqttBridgePublisher.this.connected(); }
                        @Override public void onConnectionLost() { MqttBridgePublisher.this.connectionLost(); }
                        @Override public void onMessage(String topic, String payload) {
                            MqttBridgePublisher.this.message(topic, payload);
                        }
                    });
        } catch (RuntimeException ignored) {
            connected = false;
        }
    }

    /** Must be called only with values produced by MqttTelemetryMapper. */
    public synchronized void onTelemetry(boolean available, boolean fresh, Map<String, String> states) {
        if (states == null) states = Collections.emptyMap();
        lastStates = Collections.unmodifiableMap(new LinkedHashMap<>(states));
        telemetryFresh = available && fresh && !lastStates.isEmpty();
        if (connected && !closed) publishCurrentState();
    }

    public synchronized boolean isConnected() { return connected && !closed; }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (connected) safePublish(availabilityTopic, PAYLOAD_OFFLINE, 1, true);
        connected = false;
        try { transport.disconnect(); } catch (RuntimeException ignored) { }
    }

    private synchronized void connected() {
        if (closed) return;
        connected = true;
        publishDiscovery();
        safeSubscribe(HA_BIRTH_TOPIC, 1);
        publishCurrentState();
    }

    private synchronized void connectionLost() {
        connected = false;
    }

    private synchronized void message(String topic, String payload) {
        if (closed || !connected || !HA_BIRTH_TOPIC.equals(topic) || payload == null
                || !HA_BIRTH_ONLINE.equalsIgnoreCase(payload.trim())) return;
        publishDiscovery();
        publishCurrentState();
    }

    private void publishDiscovery() {
        for (MqttDiscoveryCatalog.Definition definition : definitions) {
            safePublish(definition.getDiscoveryTopic(), definition.toJson(), 1, true);
        }
    }

    private void publishCurrentState() {
        if (!telemetryFresh) {
            safePublish(availabilityTopic, PAYLOAD_OFFLINE, 1, true);
            return;
        }
        for (MqttDiscoveryCatalog.Definition definition : definitions) {
            String value = lastStates.get(definition.getObjectId());
            if (value == null || value.isEmpty()) value = "unknown";
            safePublish(definition.getStateTopic(), value, 1, true);
        }
        safePublish(availabilityTopic, PAYLOAD_ONLINE, 1, true);
    }

    private void safePublish(String topic, String payload, int qos, boolean retained) {
        try { transport.publish(topic, payload, qos, retained); }
        catch (RuntimeException ignored) { }
    }

    private void safeSubscribe(String topic, int qos) {
        try { transport.subscribe(topic, qos); }
        catch (RuntimeException ignored) { }
    }

    public interface Transport {
        void connect(String brokerUri, String clientId, String username, char[] password,
                String willTopic, String willPayload, Listener listener);
        void publish(String topic, String payload, int qos, boolean retained);
        void subscribe(String topic, int qos);
        boolean isConnected();
        void disconnect();

        interface Listener {
            void onConnected();
            void onConnectionLost();
            void onMessage(String topic, String payload);
            default void onMessage(String topic, String payload, boolean retained, boolean duplicate) {
                onMessage(topic, payload);
            }
        }
    }
}
