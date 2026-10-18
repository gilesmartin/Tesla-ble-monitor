package org.opencharge.teslable;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-GCM envelope for the API bearer token; key material is supplied by a separate Keystore alias. */
public final class ApiTokenCipher {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private ApiTokenCipher() { }

    public static String encrypt(SecretKey key, String plaintext) throws GeneralSecurityException {
        if (key == null || plaintext == null) throw new IllegalArgumentException("key and token are required");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        if (iv == null || iv.length != IV_BYTES) throw new GeneralSecurityException("unexpected AES-GCM IV length");
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] envelope = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, envelope, 0, iv.length);
        System.arraycopy(ciphertext, 0, envelope, iv.length, ciphertext.length);
        return Base64.getEncoder().withoutPadding().encodeToString(envelope);
    }

    public static String decrypt(SecretKey key, String encoded) throws GeneralSecurityException {
        if (key == null || encoded == null) throw new IllegalArgumentException("key and envelope are required");
        final byte[] envelope;
        try { envelope = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException e) { throw new GeneralSecurityException("invalid encrypted token envelope", e); }
        if (envelope.length < IV_BYTES + TAG_BITS / 8) throw new GeneralSecurityException("truncated encrypted token envelope");
        byte[] iv = Arrays.copyOfRange(envelope, 0, IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(envelope, IV_BYTES, envelope.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }
}
