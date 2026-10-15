package org.opencharge.teslable;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Private, Keystore-backed MQTT configuration. Passwords are never stored as preference values. */
public final class MqttConfigStore {
    private static final String PREFS_NAME = "tesla_ble_mqtt_v1";
    private static final String PREF_ENABLED = "enabled";
    private static final String PREF_BROKER_URI = "broker_uri";
    private static final String PREF_ALLOW_PLAINTEXT = "allow_plaintext";
    private static final String PREF_DEVICE_ID = "device_id";
    private static final String PREF_CREDENTIALS = "credentials_envelope";
    private static final String KEY_ALIAS = "tesla_ble_mqtt_credentials_v1";
    private static final Pattern DEVICE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private final SharedPreferences preferences;

    public MqttConfigStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Blank credentials preserve an existing encrypted envelope; one blank field is rejected. */
    public synchronized boolean save(String brokerUri, boolean allowPlaintext, boolean enabled,
            String username, String password) {
        try {
            MqttBridgeConfig.validateBrokerUri(brokerUri, allowPlaintext);
            boolean usernameEmpty = username == null || username.isEmpty();
            boolean passwordEmpty = password == null || password.isEmpty();
            if (usernameEmpty != passwordEmpty) return false;

            String encrypted = preferences.getString(PREF_CREDENTIALS, null);
            if (!usernameEmpty) {
                MqttBridgeConfig.validateCredentials(username, password);
                encrypted = MqttCredentialCipher.encrypt(getKey(true), username, password);
            }
            if (encrypted == null) return false;

            String deviceId = getOrCreateDeviceId();
            if (!DEVICE_ID.matcher(deviceId).matches()) return false;
            return preferences.edit()
                    .putBoolean(PREF_ENABLED, enabled)
                    .putString(PREF_BROKER_URI, brokerUri.trim())
                    .putBoolean(PREF_ALLOW_PLAINTEXT, allowPlaintext)
                    .putString(PREF_DEVICE_ID, deviceId)
                    .putString(PREF_CREDENTIALS, encrypted)
                    .commit();
        } catch (Exception ignored) {
            // Do not log broker settings or credential material.
            return false;
        }
    }

    /** Returns null unless the user explicitly enabled a complete, valid configuration. */
    public synchronized Settings loadForService() {
        if (!preferences.getBoolean(PREF_ENABLED, false)) return null;
        String brokerUri = preferences.getString(PREF_BROKER_URI, null);
        String encrypted = preferences.getString(PREF_CREDENTIALS, null);
        String deviceId = preferences.getString(PREF_DEVICE_ID, null);
        boolean allowPlaintext = preferences.getBoolean(PREF_ALLOW_PLAINTEXT, false);
        if (brokerUri == null || encrypted == null || deviceId == null || !DEVICE_ID.matcher(deviceId).matches()) {
            return null;
        }
        try {
            MqttBridgeConfig.validateBrokerUri(brokerUri, allowPlaintext);
            MqttCredentialCipher.Credentials credentials = MqttCredentialCipher.decrypt(getKey(false), encrypted);
            MqttBridgeConfig.validateCredentials(credentials.getUsername(), credentials.getPassword());
            return new Settings(brokerUri, allowPlaintext, deviceId,
                    credentials.getUsername(), credentials.getPassword());
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean isEnabled() {
        return preferences.getBoolean(PREF_ENABLED, false);
    }

    public boolean hasCredentials() {
        String encrypted = preferences.getString(PREF_CREDENTIALS, null);
        return encrypted != null && !encrypted.isEmpty();
    }

    public String getBrokerUriForUi() { return preferences.getString(PREF_BROKER_URI, ""); }

    public boolean isPlaintextAllowed() { return preferences.getBoolean(PREF_ALLOW_PLAINTEXT, false); }

    public synchronized String getDeviceId() {
        return getOrCreateDeviceId();
    }

    private String getOrCreateDeviceId() {
        String existing = preferences.getString(PREF_DEVICE_ID, null);
        if (existing != null && DEVICE_ID.matcher(existing).matches()) return existing;
        String generated = UUID.randomUUID().toString().replace("-", "");
        preferences.edit().putString(PREF_DEVICE_ID, generated).commit();
        return generated;
    }

    private static SecretKey getKey(boolean create) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            if (!create) return null;
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            generator.generateKey();
        }
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }

    public static final class Settings {
        private final String brokerUri;
        private final boolean allowPlaintext;
        private final String deviceId;
        private final String username;
        private final String password;

        private Settings(String brokerUri, boolean allowPlaintext, String deviceId,
                String username, String password) {
            this.brokerUri = brokerUri;
            this.allowPlaintext = allowPlaintext;
            this.deviceId = deviceId;
            this.username = username;
            this.password = password;
        }

        public String getBrokerUri() { return brokerUri; }
        public boolean isAllowPlaintext() { return allowPlaintext; }
        public String getDeviceId() { return deviceId; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
    }
}
