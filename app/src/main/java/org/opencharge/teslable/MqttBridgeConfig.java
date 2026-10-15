package org.opencharge.teslable;

import java.net.URI;
import java.net.URISyntaxException;

/** Validation helpers for broker settings; secret values are never included in errors. */
public final class MqttBridgeConfig {
    private MqttBridgeConfig() { }

    public static void validateBrokerUri(String brokerUri, boolean allowPlaintext) {
        if (brokerUri == null || brokerUri.length() > 255) {
            throw new IllegalArgumentException("invalid MQTT broker URI");
        }
        final URI uri;
        try {
            uri = new URI(brokerUri.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid MQTT broker URI");
        }
        String scheme = uri.getScheme();
        boolean tls = "ssl".equalsIgnoreCase(scheme);
        boolean plaintext = "tcp".equalsIgnoreCase(scheme);
        if ((!tls && !plaintext) || (plaintext && !allowPlaintext)
                || uri.getHost() == null || uri.getHost().isEmpty()
                || uri.getPort() < 1 || uri.getPort() > 65535
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("invalid MQTT broker URI or transport policy");
        }
    }

    public static void validateCredentials(String username, String password) {
        if (username == null || password == null || username.trim().isEmpty() || password.isEmpty()
                || username.length() > 128 || password.length() > 512
                || containsControl(username) || containsControl(password)) {
            throw new IllegalArgumentException("MQTT username and password are required");
        }
    }

    private static boolean containsControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7f) return true;
        }
        return false;
    }
}
