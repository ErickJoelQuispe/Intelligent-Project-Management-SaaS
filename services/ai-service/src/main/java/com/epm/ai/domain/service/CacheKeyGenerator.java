package com.epm.ai.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for generating deterministic SHA-256 cache keys from prompt parts.
 *
 * <p>Normalization rules:
 * <ul>
 *   <li>Each part is trimmed (leading/trailing whitespace removed)</li>
 *   <li>Internal whitespace runs are collapsed to a single space</li>
 *   <li>Parts are joined with {@code ":"} before hashing</li>
 * </ul>
 */
public final class CacheKeyGenerator {

    private CacheKeyGenerator() {
        // utility class — no instantiation
    }

    /**
     * Generates a hex-encoded SHA-256 cache key from the given parts.
     *
     * @param parts one or more string parts to combine into a cache key
     * @return lowercase hex-encoded SHA-256 hash of the normalized, joined parts
     */
    public static String generate(String... parts) {
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i] == null ? "" : parts[i].trim().replaceAll("\\s+", " ");
            normalized.append(part);
            if (i < parts.length - 1) {
                normalized.append(':');
            }
        }
        return sha256Hex(normalized.toString());
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hexByte = Integer.toHexString(0xff & b);
                if (hexByte.length() == 1) {
                    hex.append('0');
                }
                hex.append(hexByte);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
