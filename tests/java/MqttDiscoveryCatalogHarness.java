package org.opencharge.teslable;

public final class MqttDiscoveryCatalogHarness {
    public static void main(String[] args) {
        for (MqttDiscoveryCatalog.Definition definition : MqttDiscoveryCatalog.create("test_vehicle_123")) {
            System.out.println(definition.getDiscoveryTopic() + "|" + definition.toJson());
        }
    }
}
