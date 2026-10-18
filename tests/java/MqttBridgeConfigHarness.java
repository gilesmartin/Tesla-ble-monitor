package org.opencharge.teslable;

public final class MqttBridgeConfigHarness {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String scenario = args.length == 0 ? "" : args[0];
        switch (scenario) {
            case "tls":
                MqttBridgeConfig.validateBrokerUri("ssl://broker.example:8883", false);
                break;
            case "plain-denied": {
                boolean rejected = false;
                try { MqttBridgeConfig.validateBrokerUri("tcp://broker.example:1883", false); }
                catch (IllegalArgumentException expected) { rejected = true; }
                check(rejected, "plaintext transport must require explicit opt-in");
                break;
            }
            case "plain-allowed":
                MqttBridgeConfig.validateBrokerUri("tcp://broker.example:1883", true);
                break;
            case "userinfo-denied": {
                boolean rejected = false;
                try { MqttBridgeConfig.validateBrokerUri("ssl://user:pass@broker.example:8883", false); }
                catch (IllegalArgumentException expected) { rejected = true; }
                check(rejected, "credentials must not be embedded in broker URI");
                break;
            }
            default: throw new AssertionError("unknown scenario");
        }
        System.out.println("MqttBridgeConfigHarness: PASS " + scenario);
    }
}
