package org.opencharge.teslable;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Stores the local API bearer token as AES-GCM ciphertext under a dedicated Keystore key. */
public final class ApiTokenStore {
    private static final String PREFS_NAME = "tesla_ble_api_v1";
    private static final String PREF_TOKEN = "token_envelope";
    private static final String API_ALIAS = "tesla_ble_api_token_v1";
    private static final Object LOCK = new Object();
    private final SharedPreferences preferences;

    public ApiTokenStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean setToken(String token) {
        if (!isValidToken(token)) return false;
        synchronized (LOCK) {
            try {
                SecretKey key = getKey(true);
                String encrypted = ApiTokenCipher.encrypt(key, token);
                return preferences.edit().putString(PREF_TOKEN, encrypted).commit();
            } catch (Exception ignored) {
                // Never include token material or crypto-provider details in logs.
                return false;
            }
        }
    }

    public String getToken() {
        synchronized (LOCK) {
            String encrypted = preferences.getString(PREF_TOKEN, null);
            if (encrypted == null) return null;
            try {
                SecretKey key = getKey(false);
                return key == null ? null : ApiTokenCipher.decrypt(key, encrypted);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    public boolean isConfigured() { return getToken() != null; }

    private static boolean isValidToken(String token) {
        if (token == null || token.length() < 32 || token.length() > 512) return false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c < 0x21 || c > 0x7e) return false;
        }
        return token.getBytes(StandardCharsets.UTF_8).length >= 32;
    }

    private static SecretKey getKey(boolean create) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(API_ALIAS)) {
            if (!create) return null;
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(API_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            generator.generateKey();
        }
        return (SecretKey) keyStore.getKey(API_ALIAS, null);
    }
}
