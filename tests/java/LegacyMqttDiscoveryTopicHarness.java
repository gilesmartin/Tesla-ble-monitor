package org.opencharge.teslable;

public final class LegacyMqttDiscoveryTopicHarness {
    public static void main(String[] args) {
        String vin = args.length > 0 ? args[0] : "5YJ3E1EA7JF000001";
        for (LegacyMqttDiscoveryCatalog.Definition definition : LegacyMqttDiscoveryCatalog.create(vin)) {
            System.out.println(definition.getDiscoveryTopic());
        }
    }
}
