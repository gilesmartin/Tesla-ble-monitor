package org.opencharge.teslable;

import java.net.URI;
import java.net.URISyntaxException;

/** Validation helpers for broker settings; secret values are never included in errors. */
public final class MqttBridgeConfig {
    private MqttBridgeConfig() { }

    public static void validateBrokerUri(String brokerUri, boolean allowPlaintext) {
        if (brokerUri == null || brokerUri.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a broker URI such as ssl://host:8883.");
        }
        if (brokerUri.length() > 255) {
            throw new IllegalArgumentException("Broker URI must be at most 255 characters.");
        }
        final URI uri;
        try {
            uri = new URI(brokerUri.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Broker URI is not valid. Use ssl://host:port or tcp://host:port.");
        }
        String scheme = uri.getScheme();
        boolean tls = "ssl".equalsIgnoreCase(scheme);
        boolean plaintext = "tcp".equalsIgnoreCase(scheme);
        if (!tls && !plaintext) {
            throw new IllegalArgumentException("Use ssl:// or tcp:// for the broker URI.");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Enter credentials in the username and password fields, not in the broker URI.");
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw new IllegalArgumentException("Enter a valid broker hostname or IP address.");
        }
        if (uri.getPort() < 1 || uri.getPort() > 65535) {
            throw new IllegalArgumentException("Specify a broker port between 1 and 65535.");
        }
        if (uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("Broker URI must not include a path, query, or fragment.");
        }
        if (plaintext && !allowPlaintext) {
            throw new IllegalArgumentException("Enable Allow plaintext MQTT for tcp://, or use an ssl:// broker.");
        }
    }

    public static void validateCredentials(String username, String password) {
        validateUsername(username);
        validatePassword(password);
    }

    public static void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter an MQTT username.");
        }
        if (username.length() > 128 || containsControl(username)) {
            throw new IllegalArgumentException("MQTT username must be at most 128 characters and contain no control characters.");
        }
    }

    public static void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Enter an MQTT password.");
        }
        if (password.length() > 512 || containsControl(password)) {
            throw new IllegalArgumentException("MQTT password must be at most 512 characters and contain no control characters.");
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
