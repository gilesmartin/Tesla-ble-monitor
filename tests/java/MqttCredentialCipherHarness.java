package org.opencharge.teslable;

import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public final class MqttCredentialCipherHarness {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static SecretKey newKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }

    public static void main(String[] args) throws Exception {
        String user = "fixture-user";
        String pass = "fixture-password-only";
        SecretKey key = newKey();
        String envelope = MqttCredentialCipher.encrypt(key, user, pass);
        check(!envelope.contains(user) && !envelope.contains(pass), "credential envelope contains fixture plaintext");
        MqttCredentialCipher.Credentials restored = MqttCredentialCipher.decrypt(key, envelope);
        check(user.equals(restored.getUsername()), "username did not round-trip");
        check(pass.equals(restored.getPassword()), "password did not round-trip");

        boolean rejected = false;
        try { MqttCredentialCipher.decrypt(newKey(), envelope); }
        catch (Exception expected) { rejected = true; }
        check(rejected, "credentials decrypted with the wrong key");

        byte[] raw = java.util.Base64.getDecoder().decode(envelope);
        raw[raw.length - 1] ^= 1;
        String tampered = java.util.Base64.getEncoder().withoutPadding().encodeToString(raw);
        rejected = false;
        try { MqttCredentialCipher.decrypt(key, tampered); }
        catch (Exception expected) { rejected = true; }
        check(rejected, "tampered credential envelope was accepted");
        System.out.println("MqttCredentialCipherHarness: PASS confidentiality and integrity");
    }
}
