package org.opencharge.teslable;

import java.nio.charset.StandardCharsets;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public final class ApiTokenCipherHarness {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();
        SecretKey wrongKey = generator.generateKey();
        String token = "test-bearer-token-not-to-be-stored-in-plaintext-0123456789";
        String encrypted = ApiTokenCipher.encrypt(key, token);
        check(!encrypted.contains(token), "stored envelope contains plaintext token");
        check(token.equals(ApiTokenCipher.decrypt(key, encrypted)), "encrypted token did not round-trip");
        boolean rejected = false;
        try { ApiTokenCipher.decrypt(wrongKey, encrypted); }
        catch (Exception expected) { rejected = true; }
        check(rejected, "ciphertext decrypted with the wrong key");
        byte[] raw = java.util.Base64.getDecoder().decode(encrypted);
        raw[raw.length - 1] ^= 1;
        String tampered = java.util.Base64.getEncoder().withoutPadding().encodeToString(raw);
        rejected = false;
        try { ApiTokenCipher.decrypt(key, tampered); }
        catch (Exception expected) { rejected = true; }
        check(rejected, "tampered token ciphertext was accepted");
        System.out.println("ApiTokenCipherHarness: PASS (AES-GCM, confidentiality, integrity)");
    }
}
