package com.ragcraft.channel.service;

import com.ragcraft.channel.ChannelProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** AES-GCM encryption for bot tokens at rest (the FastAPI backend uses Fernet for the same purpose). */
@Component
public class TokenCipher {

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(ChannelProperties properties) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(properties.getEncryptionKey().getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception ex) {
            throw new IllegalStateException("Token could not be encrypted.", ex);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] in = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, in, 0, 12));
            return new String(cipher.doFinal(in, 12, in.length - 12), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Token could not be decrypted.", ex);
        }
    }

    public static String mask(String token) {
        if (token == null || token.length() < 10) return "****";
        return token.substring(0, Math.min(6, token.indexOf(':') > 0 ? token.indexOf(':') : 6)) + ":****" + token.substring(token.length() - 4);
    }
}
