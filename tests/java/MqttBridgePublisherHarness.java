package org.opencharge.teslable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MqttBridgePublisherHarness {
    private static final class Publication {
        final String topic, payload;
        final int qos;
        final boolean retained;
        Publication(String topic, String payload, int qos, boolean retained) {
            this.topic=topic; this.payload=payload; this.qos=qos; this.retained=retained;
        }
    }

    private static final class FakeTransport implements MqttBridgePublisher.Transport {
        final List<Publication> publications = new ArrayList<>();
        final List<String> subscriptions = new ArrayList<>();
        Listener listener;
        String clientId;
        boolean connected;
        @Override public void connect(String uri, String clientId, String username, char[] password,
                String willTopic, String willPayload, Listener callback) {
            this.clientId=clientId; listener=callback; connected=true; callback.onConnected();
        }
        @Override public void publish(String topic, String payload, int qos, boolean retained) {
            publications.add(new Publication(topic,payload,qos,retained));
        }
        @Override public void subscribe(String topic, int qos) { subscriptions.add(topic); }
        @Override public boolean isConnected() { return connected; }
        @Override public void disconnect() { connected=false; }
        void message(String topic, String payload) { listener.onMessage(topic,payload); }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static MqttBridgePublisher create(FakeTransport fake) {
        return new MqttBridgePublisher("ssl://broker.example:8883", false,
                "fixture-user", "fixture-password", "device_test", fake);
    }

    private static void fresh() {
        FakeTransport fake=new FakeTransport();
        MqttBridgePublisher bridge=create(fake);
        bridge.start();
        check(fake.publications.size()==5, "connection should publish four discovery definitions and an offline marker");
        int initialDiscovery=0;
        for (Publication item:fake.publications) if (item.topic.startsWith("homeassistant/")) initialDiscovery++;
        check(initialDiscovery==4, "startup discovery count must be four");
        check(fake.clientId.length() <= 23, "MQTT 3 client identifier should remain under the baseline broker limit");
        check("offline".equals(payloadFor(fake,"tesla_ble_monitor/device_test/availability")),
                "publisher must stay unavailable until fresh telemetry arrives");
        check(fake.subscriptions.size()==1 && "homeassistant/status".equals(fake.subscriptions.get(0)),
                "only the Home Assistant birth topic may be subscribed");
        fake.publications.clear();
        Map<String,String> states=new LinkedHashMap<>();
        states.put("battery_percent","71.0");
        states.put("charging","true");
        bridge.onTelemetry(true,true,states);
        check(fake.publications.size()==5, "fresh update should publish four states and availability");
        for (Publication item:fake.publications) check(item.qos==1 && item.retained,
                "state and availability publications must be QoS 1 retained");
        check("unknown".equals(payloadFor(fake,"tesla_ble_monitor/device_test/usable_battery_percent")),
                "missing fresh fields must clear retained values instead of preserving stale state");
        check("online".equals(payloadFor(fake,"tesla_ble_monitor/device_test/availability")),
                "fresh telemetry should mark availability online");
        for (Publication item:fake.publications) check(!item.topic.contains("command"),
                "read-only publisher must not emit a command topic");
        bridge.close();
    }

    private static void stale() {
        FakeTransport fake=new FakeTransport();
        MqttBridgePublisher bridge=create(fake);
        bridge.start();
        fake.publications.clear();
        bridge.onTelemetry(true,false,java.util.Collections.emptyMap());
        check(fake.publications.size()==1, "stale update must only publish availability");
        check("offline".equals(fake.publications.get(0).payload), "stale data must mark device offline");
        check(fake.publications.get(0).retained, "offline marker must be retained");
        bridge.close();
    }

    private static void birth() {
        FakeTransport fake=new FakeTransport();
        MqttBridgePublisher bridge=create(fake);
        bridge.start();
        fake.publications.clear();
        fake.message("homeassistant/status","online");
        int discovery=0;
        for (Publication item:fake.publications) if (item.topic.startsWith("homeassistant/")) discovery++;
        check(discovery==4, "Home Assistant birth must republish all four discovery records");
        bridge.close();
    }

    private static String payloadFor(FakeTransport fake,String topic) {
        for (Publication item:fake.publications) if (topic.equals(item.topic)) return item.payload;
        return null;
    }

    public static void main(String[] args) {
        String scenario=args.length==0?"":args[0];
        if ("fresh".equals(scenario)) fresh();
        else if ("stale".equals(scenario)) stale();
        else if ("birth".equals(scenario)) birth();
        else throw new AssertionError("unknown scenario");
        System.out.println("MqttBridgePublisherHarness: PASS " + scenario);
    }
}
