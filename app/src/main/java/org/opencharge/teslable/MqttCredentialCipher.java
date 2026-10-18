package org.opencharge.teslable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-GCM envelope for broker credentials, separate from vehicle keys and API tokens. */
public final class MqttCredentialCipher {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MAX_USERNAME_BYTES = 512;
    private static final int MAX_PASSWORD_BYTES = 2048;
    private static final byte[] AAD = "tesla_ble_mqtt_credentials_v1".getBytes(StandardCharsets.UTF_8);

    private MqttCredentialCipher() { }

    public static String encrypt(SecretKey key, String username, String password)
            throws GeneralSecurityException {
        if (key == null || username == null || password == null
                || username.trim().isEmpty() || password.isEmpty()) {
            throw new IllegalArgumentException("MQTT credentials are required");
        }
        byte[] userBytes = username.getBytes(StandardCharsets.UTF_8);
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        if (userBytes.length > MAX_USERNAME_BYTES || passwordBytes.length > MAX_PASSWORD_BYTES) {
            Arrays.fill(userBytes, (byte) 0);
            Arrays.fill(passwordBytes, (byte) 0);
            throw new IllegalArgumentException("MQTT credentials exceed supported size");
        }

        byte[] plaintext = null;
        try {
            plaintext = ByteBuffer.allocate(8 + userBytes.length + passwordBytes.length)
                    .putInt(userBytes.length).put(userBytes)
                    .putInt(passwordBytes.length).put(passwordBytes).array();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length != IV_BYTES) throw new GeneralSecurityException("unexpected AES-GCM IV length");
            cipher.updateAAD(AAD);
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] envelope = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, envelope, 0, iv.length);
            System.arraycopy(ciphertext, 0, envelope, iv.length, ciphertext.length);
            Arrays.fill(ciphertext, (byte) 0);
            return Base64.getEncoder().withoutPadding().encodeToString(envelope);
        } finally {
            Arrays.fill(userBytes, (byte) 0);
            Arrays.fill(passwordBytes, (byte) 0);
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }
    }

    public static Credentials decrypt(SecretKey key, String encoded) throws GeneralSecurityException {
        if (key == null || encoded == null) throw new IllegalArgumentException("key and envelope are required");
        final byte[] envelope;
        try {
            envelope = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("invalid encrypted MQTT credential envelope");
        }
        if (envelope.length < IV_BYTES + TAG_BITS / 8 + 8) {
            Arrays.fill(envelope, (byte) 0);
            throw new GeneralSecurityException("truncated encrypted MQTT credential envelope");
        }

        byte[] iv = Arrays.copyOfRange(envelope, 0, IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(envelope, IV_BYTES, envelope.length);
        byte[] plaintext = null;
        byte[] userBytes = null;
        byte[] passwordBytes = null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(AAD);
            plaintext = cipher.doFinal(ciphertext);
            ByteBuffer input = ByteBuffer.wrap(plaintext);
            if (input.remaining() < 8) throw new GeneralSecurityException("invalid MQTT credential envelope");
            int usernameLength = input.getInt();
            if (usernameLength < 1 || usernameLength > MAX_USERNAME_BYTES
                    || input.remaining() < usernameLength + 4) {
                throw new GeneralSecurityException("invalid MQTT credential envelope");
            }
            userBytes = new byte[usernameLength];
            input.get(userBytes);
            int passwordLength = input.getInt();
            if (passwordLength < 1 || passwordLength > MAX_PASSWORD_BYTES || input.remaining() != passwordLength) {
                throw new GeneralSecurityException("invalid MQTT credential envelope");
            }
            passwordBytes = new byte[passwordLength];
            input.get(passwordBytes);
            String username = new String(userBytes, StandardCharsets.UTF_8);
            String password = new String(passwordBytes, StandardCharsets.UTF_8);
            return new Credentials(username, password);
        } catch (java.nio.BufferUnderflowException e) {
            throw new GeneralSecurityException("invalid MQTT credential envelope", e);
        } finally {
            Arrays.fill(envelope, (byte) 0);
            Arrays.fill(iv, (byte) 0);
            Arrays.fill(ciphertext, (byte) 0);
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
            if (userBytes != null) Arrays.fill(userBytes, (byte) 0);
            if (passwordBytes != null) Arrays.fill(passwordBytes, (byte) 0);
        }
    }

    public static final class Credentials {
        private final String username;
        private final String password;

        private Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() { return username; }
        public String getPassword() { return password; }
    }
}
