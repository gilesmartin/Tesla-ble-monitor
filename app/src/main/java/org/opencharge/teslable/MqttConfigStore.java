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
    private static final String PREF_LEGACY = "legacy_compatibility";
    private static final String PREF_CONTROLS = "legacy_controls";
    private static final String KEY_ALIAS = "tesla_ble_mqtt_credentials_v1";
    private static final Pattern DEVICE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private final SharedPreferences preferences;
    private final KeyAccess keyAccess;

    interface KeyAccess { SecretKey get(boolean create) throws Exception; }

    public MqttConfigStore(Context context) {
        this(context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                MqttConfigStore::getKey);
    }

    // Package-private injection for executable host tests; production always uses Android Keystore.
    MqttConfigStore(SharedPreferences preferences, KeyAccess keyAccess) {
        this.preferences = preferences;
        this.keyAccess = keyAccess;
    }

    /** Compatibility API; detailed failures are available through saveWithResult(). */
    public synchronized boolean save(String brokerUri, boolean allowPlaintext, boolean enabled,
            String username, String password) {
        return saveWithResult(brokerUri, allowPlaintext, enabled, username, password).isSuccess();
    }

    /** Validate everything before creating an editor or changing the existing configuration. */
    public synchronized SaveResult saveWithResult(String brokerUri, boolean allowPlaintext,
            boolean enabled, String username, String password) {
        return saveWithResult(brokerUri, allowPlaintext, enabled, username, password,
                isLegacyCompatibilityEnabled(), isLegacyControlsEnabled());
    }

    public synchronized SaveResult saveWithResult(String brokerUri, boolean allowPlaintext,
            boolean enabled, String username, String password, boolean legacy, boolean controls) {
        if (controls && !legacy) return SaveResult.failure(SaveResult.Field.OPTIONS,
                "Enable legacy compatibility before enabling vehicle controls.");
        try {
            MqttBridgeConfig.validateBrokerUri(brokerUri, allowPlaintext);
        } catch (IllegalArgumentException invalid) {
            return SaveResult.failure(SaveResult.Field.BROKER, invalid.getMessage());
        }
        username = username == null ? "" : username;
        password = password == null ? "" : password;
        String encrypted = preferences.getString(PREF_CREDENTIALS, null);
        if (password.isEmpty()) {
            if (encrypted == null || encrypted.isEmpty()) {
                return SaveResult.failure(username.isEmpty() ? SaveResult.Field.USERNAME : SaveResult.Field.PASSWORD,
                        username.isEmpty() ? "Enter an MQTT username and password." : "Enter an MQTT password.");
            }
            final MqttCredentialCipher.Credentials saved;
            try {
                saved = MqttCredentialCipher.decrypt(keyAccess.get(false), encrypted);
                MqttBridgeConfig.validateCredentials(saved.getUsername(), saved.getPassword());
            } catch (Exception unavailable) {
                return SaveResult.failure(SaveResult.Field.PASSWORD,
                        "Saved credentials could not be decrypted. Re-enter both username and password.");
            }
            if (!username.isEmpty() && !username.equals(saved.getUsername())) {
                return SaveResult.failure(SaveResult.Field.PASSWORD,
                        "Enter a new password when changing the MQTT username.");
            }
        } else {
            try {
                MqttBridgeConfig.validateUsername(username);
            } catch (IllegalArgumentException invalid) {
                return SaveResult.failure(SaveResult.Field.USERNAME, invalid.getMessage());
            }
            try {
                MqttBridgeConfig.validatePassword(password);
            } catch (IllegalArgumentException invalid) {
                return SaveResult.failure(SaveResult.Field.PASSWORD, invalid.getMessage());
            }
            try {
                encrypted = MqttCredentialCipher.encrypt(keyAccess.get(true), username, password);
            } catch (Exception unavailable) {
                return SaveResult.failure(SaveResult.Field.STORAGE,
                        "Could not encrypt MQTT credentials. Settings were not saved.");
            }
        }
        String deviceId = preferences.getString(PREF_DEVICE_ID, null);
        if (deviceId == null) deviceId = UUID.randomUUID().toString().replace("-", "");
        if (!DEVICE_ID.matcher(deviceId).matches()) {
            return SaveResult.failure(SaveResult.Field.STORAGE, "The saved MQTT device ID is invalid. Settings were not saved.");
        }
        // Android updates the live preference map even when disk commit reports failure.
        // Restore the prior values before reporting failure, especially the control opt-ins.
        final String oldBroker = preferences.getString(PREF_BROKER_URI, null);
        final String oldDevice = preferences.getString(PREF_DEVICE_ID, null);
        final String oldEnvelope = preferences.getString(PREF_CREDENTIALS, null);
        final boolean oldEnabled = preferences.getBoolean(PREF_ENABLED, false);
        final boolean oldPlaintext = preferences.getBoolean(PREF_ALLOW_PLAINTEXT, false);
        final boolean oldLegacy = preferences.getBoolean(PREF_LEGACY, false);
        final boolean oldControls = preferences.getBoolean(PREF_CONTROLS, false);
        Runnable rollback = () -> {
            try {
                preferences.edit().putString(PREF_BROKER_URI, oldBroker).putString(PREF_DEVICE_ID, oldDevice)
                        .putString(PREF_CREDENTIALS, oldEnvelope).putBoolean(PREF_ENABLED, oldEnabled)
                        .putBoolean(PREF_ALLOW_PLAINTEXT, oldPlaintext).putBoolean(PREF_LEGACY, oldLegacy)
                        .putBoolean(PREF_CONTROLS, oldControls).commit();
            } catch (RuntimeException ignored) { /* The storage failure is reported below; no service reload occurs. */ }
        };
        try {
            boolean committed = preferences.edit()
                    .putBoolean(PREF_ENABLED, enabled)
                    .putBoolean(PREF_LEGACY, legacy)
                    .putBoolean(PREF_CONTROLS, controls)
                    .putString(PREF_BROKER_URI, brokerUri.trim())
                    .putBoolean(PREF_ALLOW_PLAINTEXT, allowPlaintext)
                    .putString(PREF_DEVICE_ID, deviceId)
                    .putString(PREF_CREDENTIALS, encrypted)
                    .commit();
            if (!committed) rollback.run();
            return committed ? SaveResult.success() : SaveResult.failure(SaveResult.Field.STORAGE,
                    "Could not write settings to device storage. Settings were not saved.");
        } catch (RuntimeException unavailable) {
            rollback.run();
            return SaveResult.failure(SaveResult.Field.STORAGE,
                    "Could not write settings to device storage. Settings were not saved.");
        }
    }

    public static final class SaveResult {
        public enum Field { NONE, BROKER, USERNAME, PASSWORD, STORAGE, OPTIONS }
        private final Field field;
        private final String message;
        private SaveResult(Field field, String message) { this.field = field; this.message = message; }
        static SaveResult success() { return new SaveResult(Field.NONE, "Saved."); }
        static SaveResult failure(Field field, String message) { return new SaveResult(field, message); }
        public boolean isSuccess() { return field == Field.NONE; }
        public Field getField() { return field; }
        public String getMessage() { return message; }
    }

    /** Only the username leaves the store for display; the password never enters a View. */
    public synchronized String getUsernameForUi() {
        String encrypted = preferences.getString(PREF_CREDENTIALS, null);
        if (encrypted == null || encrypted.isEmpty()) return "";
        try {
            return MqttCredentialCipher.decrypt(keyAccess.get(false), encrypted).getUsername();
        } catch (Exception unavailable) {
            return "";
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
            MqttCredentialCipher.Credentials credentials = MqttCredentialCipher.decrypt(keyAccess.get(false), encrypted);
            MqttBridgeConfig.validateCredentials(credentials.getUsername(), credentials.getPassword());
            return new Settings(brokerUri, allowPlaintext, deviceId,
                    credentials.getUsername(), credentials.getPassword(),
                    isLegacyCompatibilityEnabled(), isLegacyControlsEnabled());
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean isLegacyCompatibilityEnabled() { return preferences.getBoolean(PREF_LEGACY, false); }

    public boolean isLegacyControlsEnabled() { return isLegacyCompatibilityEnabled() && preferences.getBoolean(PREF_CONTROLS, false); }

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
        private final boolean legacy, controls;

        private Settings(String brokerUri, boolean allowPlaintext, String deviceId,
                String username, String password, boolean legacy, boolean controls) {
            this.legacy = legacy;
            this.controls = controls;
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
        public boolean isLegacyCompatibilityEnabled() { return legacy; }
        public boolean isLegacyControlsEnabled() { return legacy && controls; }
    }
}
