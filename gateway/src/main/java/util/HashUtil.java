package util;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

public class HashUtil {

    // SHA-1 hash từ byte array
    public static String sha1(byte[] data) {
        return hash(data, "SHA-1");
    }

    // SHA-256 hash từ byte array
    public static String sha256(byte[] data) {
        return hash(data, "SHA-256");
    }

    // SHA-1 hash từ file
    public static String sha1(File file) {
        return hashFile(file, "SHA-1");
    }

    // SHA-256 hash từ file
    public static String sha256(File file) {
        return hashFile(file, "SHA-256");
    }

    private static String hash(byte[] data, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Hashing error", e);
        }
    }

    private static String hashFile(File file, String algorithm) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return bytesToHex(digest.digest());
        } catch (Exception e) {
            throw new RuntimeException("Hashing file error", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}