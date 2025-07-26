package com.reviewsystem.common.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Utility class for generating various types of hashes
 */
@Slf4j
public class HashUtil {

    private static final String SHA256_ALGORITHM = "SHA-256";
    private static final String MD5_ALGORITHM = "MD5";
    private static final String SHA1_ALGORITHM = "SHA-1";

    private HashUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Generate SHA-256 hash of the input string
     *
     * @param input the string to hash
     * @return SHA-256 hash as hexadecimal string
     */
    public static String generateSHA256Hash(String input) {
        if (input == null) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(SHA256_ALGORITHM);
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Failed to generate SHA-256 hash", e);
        }
    }

    /**
     * Generate SHA-256 hash and return as Base64 encoded string
     *
     * @param input the string to hash
     * @return SHA-256 hash as Base64 string
     */
    public static String generateSHA256HashBase64(String input) {
        if (input == null) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(SHA256_ALGORITHM);
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Failed to generate SHA-256 hash", e);
        }
    }

    /**
     * Generate MD5 hash of the input string
     *
     * @param input the string to hash
     * @return MD5 hash as hexadecimal string
     */
    public static String generateMD5Hash(String input) {
        if (input == null) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(MD5_ALGORITHM);
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 algorithm not available", e);
            throw new RuntimeException("Failed to generate MD5 hash", e);
        }
    }

    /**
     * Generate SHA-1 hash of the input string
     *
     * @param input the string to hash
     * @return SHA-1 hash as hexadecimal string
     */
    public static String generateSHA1Hash(String input) {
        if (input == null) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(SHA1_ALGORITHM);
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-1 algorithm not available", e);
            throw new RuntimeException("Failed to generate SHA-1 hash", e);
        }
    }

    /**
     * Generate a simple hash code for the input string
     * Useful for quick hash operations where cryptographic security is not required
     *
     * @param input the string to hash
     * @return hash code as string
     */
    public static String generateSimpleHash(String input) {
        if (input == null) {
            return null;
        }

        return String.valueOf(input.hashCode());
    }

    /**
     * Generate a content hash suitable for duplicate detection
     * Uses SHA-256 but returns a shorter hash for efficiency
     *
     * @param input the string to hash
     * @return shortened SHA-256 hash (first 16 characters)
     */
    public static String generateContentHash(String input) {
        String fullHash = generateSHA256Hash(input);
        if (fullHash == null || fullHash.length() < 16) {
            return fullHash;
        }

        // Return first 16 characters for a shorter but still unique hash
        return fullHash.substring(0, 16);
    }

    /**
     * Verify if an input string matches the given hash
     *
     * @param input the input string to verify
     * @param expectedHash the expected hash value
     * @param algorithm the hashing algorithm used ("SHA-256", "MD5", "SHA-1")
     * @return true if the input generates the expected hash
     */
    public static boolean verifyHash(String input, String expectedHash, String algorithm) {
        if (input == null || expectedHash == null || algorithm == null) {
            return false;
        }

        String actualHash;
        switch (algorithm.toUpperCase()) {
            case "SHA-256":
                actualHash = generateSHA256Hash(input);
                break;
            case "MD5":
                actualHash = generateMD5Hash(input);
                break;
            case "SHA-1":
                actualHash = generateSHA1Hash(input);
                break;
            default:
                log.warn("Unsupported hashing algorithm: {}", algorithm);
                return false;
        }

        return expectedHash.equals(actualHash);
    }

    /**
     * Generate hash from multiple input strings
     * Concatenates all inputs with a delimiter before hashing
     *
     * @param delimiter the delimiter to use between inputs
     * @param inputs the input strings to hash together
     * @return SHA-256 hash of concatenated inputs
     */
    public static String generateCombinedHash(String delimiter, String... inputs) {
        if (inputs == null || inputs.length == 0) {
            return null;
        }

        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] != null) {
                combined.append(inputs[i]);
            }
            if (i < inputs.length - 1) {
                combined.append(delimiter != null ? delimiter : "|");
            }
        }

        return generateSHA256Hash(combined.toString());
    }

    /**
     * Convert byte array to hexadecimal string
     *
     * @param bytes the byte array to convert
     * @return hexadecimal string representation
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Generate a hash suitable for review content deduplication
     * Normalizes the input by trimming, converting to lowercase, and removing extra spaces
     *
     * @param reviewContent the review content to hash
     * @param hotelId the hotel ID
     * @param rating the rating value
     * @return normalized content hash
     */
    public static String generateReviewContentHash(String reviewContent, Integer hotelId, Double rating) {
        StringBuilder contentBuilder = new StringBuilder();

        if (hotelId != null) {
            contentBuilder.append(hotelId);
        }
        contentBuilder.append("|");

        if (reviewContent != null) {
            // Normalize the content
            String normalizedContent = reviewContent.trim()
                    .toLowerCase()
                    .replaceAll("\\s+", " "); // Replace multiple spaces with single space
            contentBuilder.append(normalizedContent);
        }
        contentBuilder.append("|");

        if (rating != null) {
            contentBuilder.append(rating);
        }

        return generateSHA256Hash(contentBuilder.toString());
    }

    /**
     * Check if a hash string is valid (not null, not empty, proper format)
     *
     * @param hash the hash string to validate
     * @param expectedLength the expected length of the hash (optional, -1 to skip length check)
     * @return true if the hash is valid
     */
    public static boolean isValidHash(String hash, int expectedLength) {
        if (hash == null || hash.trim().isEmpty()) {
            return false;
        }

        // Check if hash contains only valid hexadecimal characters
        if (!hash.matches("^[a-fA-F0-9]+$")) {
            return false;
        }

        // Check length if specified
        if (expectedLength > 0 && hash.length() != expectedLength) {
            return false;
        }

        return true;
    }

    /**
     * Check if a SHA-256 hash is valid (64 characters, hex format)
     *
     * @param hash the hash string to validate
     * @return true if the hash is a valid SHA-256 hash
     */
    public static boolean isValidSHA256Hash(String hash) {
        return isValidHash(hash, 64);
    }

    /**
     * Check if an MD5 hash is valid (32 characters, hex format)
     *
     * @param hash the hash string to validate
     * @return true if the hash is a valid MD5 hash
     */
    public static boolean isValidMD5Hash(String hash) {
        return isValidHash(hash, 32);
    }
}