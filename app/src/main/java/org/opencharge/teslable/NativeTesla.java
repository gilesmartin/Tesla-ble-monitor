package org.opencharge.teslable;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Bridge to the audited-by-upstream (not audited by this app) Tesla BLE C++ protocol engine. */
public final class NativeTesla {
    static { System.loadLibrary("tesla-android"); }
    public interface Sink {
        void onMessage(String text);
        void onTelemetry(String json);
        default void onCommandResult(String json) {}
        default void onLegacyTelemetry(String json) {}
        boolean onBleWrite(byte[] bytes);
    }
    private static final String ALIAS = "tesla_ble_monitor_local_key_v1";
    private final SharedPreferences prefs;
    private final Sink sink;
    private String prefix = "";
    public NativeTesla(Context ctx, Sink sink) {
        this.sink = sink;
        this.prefs = ctx.getSharedPreferences("tesla_ble_keystore_v1", Context.MODE_PRIVATE);
    }
    public void useVehicle(String vin) { prefix = hexSha(vin) + "_"; }
    private String prefKey(String key) { return prefix + hexSha(key); }
    private static String hexSha(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : d) out.append(String.format(java.util.Locale.US,"%02x",b & 0xff));
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private SecretKey cryptKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
            kg.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS,null);
    }
    // Called synchronously by JNI, on the UI thread. Never log secret values.
    @SuppressWarnings("unused")
    private byte[] storageRead(String key) {
        try {
            String encoded = prefs.getString(prefKey(key),null);
            if (encoded == null) return null;
            byte[] raw = Base64.decode(encoded, Base64.NO_WRAP);
            if (raw.length < 29) return null;
            byte[] iv = java.util.Arrays.copyOfRange(raw,0,12);
            byte[] ciphertext = java.util.Arrays.copyOfRange(raw,12,raw.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, cryptKey(), new GCMParameterSpec(128,iv));
            return c.doFinal(ciphertext);
        } catch (Exception e) { onNativeEvent("Encrypted storage read error (keystore changed?)"); return null; }
    }
    @SuppressWarnings("unused")
    private boolean storageWrite(String key, byte[] value) {
        try {
Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
c.init(Cipher.ENCRYPT_MODE, cryptKey());

byte[] ciphertext = c.doFinal(value);
byte[] iv = c.getIV();

if (iv == null || iv.length != 12) {
    throw new IllegalStateException("Unexpected AES-GCM IV length");
}
            byte[] raw = new byte[iv.length+ciphertext.length];
            System.arraycopy(iv,0,raw,0,iv.length);
            System.arraycopy(ciphertext,0,raw,iv.length,ciphertext.length);
            return prefs.edit().putString(prefKey(key),Base64.encodeToString(raw,Base64.NO_WRAP)).commit();
        } catch (Exception e) {
            android.util.Log.e("TeslaBLE", "Encrypted storage write error");
            onNativeEvent("Encrypted storage write error");
            return false;
        }
    }
    @SuppressWarnings("unused")
    private boolean storageRemove(String key) { return prefs.edit().remove(prefKey(key)).commit(); }
    @SuppressWarnings("unused")
    private void onNativeEvent(String text) { sink.onMessage(text); }
    @SuppressWarnings("unused")
    private void onNativeTelemetry(String json) { sink.onTelemetry(json); }
    @SuppressWarnings("unused")
    private void onNativeCommandResult(String json) { sink.onCommandResult(json); }
    @SuppressWarnings("unused")
    private void onNativeLegacyTelemetry(String json) { sink.onLegacyTelemetry(json); }
    @SuppressWarnings("unused")
    private boolean nativeBleWrite(byte[] data) { return sink.onBleWrite(data); }

    /** Exercises the same encrypted SharedPreferences path as Tesla key storage, without logging secrets. */
    public boolean storageSelfTest() {
        String name = "tesla_ble_adb_test";
        byte[] test = "test-encrypted-storage-v1".getBytes(StandardCharsets.US_ASCII);
        boolean saved = storageWrite(name, test);
        boolean readBack = saved && java.util.Arrays.equals(storageRead(name), test);
        boolean removed = storageRemove(name);
        boolean success = saved && readBack && removed;
        onNativeEvent("Encrypted storage self-test: " + (success ? "PASS" : "FAIL"));
        return success;
    }

    /** Queue on the sole BLE owner; success is delivered separately after vehicle acknowledgement. */
    public boolean executeLegacy(String requestId, String operation, String[] arguments) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            throw new IllegalStateException("Vehicle commands must run on the BLE owner's Looper");
        }
        return executeLegacyOnOwner(requestId, operation, arguments);
    }
    private native boolean executeLegacyOnOwner(String requestId, String operation, String[] arguments);

    public boolean readLegacyState(String section) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            throw new IllegalStateException("Vehicle reads must run on the BLE owner's Looper");
        }
        return readLegacyStateOnOwner(section);
    }
    private native boolean readLegacyStateOnOwner(String section);

    /** Local, physically confirmed role request. Missing existing keys are rejected, never generated here. */
    public boolean pairExistingControllerKey() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            throw new IllegalStateException("Enrollment must run on the BLE owner's Looper");
        }
        return pairExistingControllerKeyOnOwner();
    }
    private native boolean pairExistingControllerKeyOnOwner();

    public native void init(String vin);
    public native void connected(boolean active);
    public native void onNotification(byte[] data);
    public native void tick();
    public native void pairMonitorKey();
    public native void readChargeState();
    public native boolean hasPrivateKey();
    public native void close();
}
