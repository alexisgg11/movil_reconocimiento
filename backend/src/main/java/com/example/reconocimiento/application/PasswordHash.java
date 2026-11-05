package com.example.reconocimiento.application;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class PasswordHash {
    private static final int ITERATIONS = 600_000;
    private static final String PREFIX = "pbkdf2-sha256$" + ITERATIONS + "$";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private PasswordHash() {}

    static String create(String password, SecureRandom random) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        Base64.Encoder encoder = Base64.getEncoder();
        return PREFIX + encoder.encodeToString(salt) + "$" + encoder.encodeToString(derive(password, salt));
    }

    static boolean matches(String password, String encoded) {
        byte[] salt = new byte[SALT_BYTES];
        byte[] expected = new byte[HASH_BYTES];
        boolean configured = encoded != null && encoded.startsWith(PREFIX);
        if (configured) {
            String[] fields = encoded.substring(PREFIX.length()).split("\\$", -1);
            if (fields.length != 2) return false;
            try {
                salt = Base64.getDecoder().decode(fields[0]);
                expected = Base64.getDecoder().decode(fields[1]);
            } catch (IllegalArgumentException invalidHash) {
                return false;
            }
            if (salt.length != SALT_BYTES || expected.length != HASH_BYTES) return false;
        }
        return MessageDigest.isEqual(expected, derive(password, salt)) && configured;
    }

    private static byte[] derive(String password, byte[] salt) {
        char[] characters = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(characters, salt, ITERATIONS, HASH_BYTES * Byte.SIZE);
        Arrays.fill(characters, '\0');
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("PBKDF2 no está disponible.", failure);
        } finally {
            spec.clearPassword();
        }
    }
}
