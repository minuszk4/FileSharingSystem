package file;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Arrays;

public class ChunkStorage {
    private final Path baseDir;

    public ChunkStorage() {
        String baseDir = System.getenv("DATA_PATH");
        this.baseDir = Paths.get(baseDir);
        try {
            if (!Files.exists(this.baseDir)) {
                Files.createDirectories(this.baseDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create chunk storage directory", e);
        }
    }

    // Lưu chunk ra đĩa, tên file = hash.hex
    public boolean saveChunk(byte[] chunkData, byte[] chunkHash) {
        Path chunkFile = baseDir.resolve(bytesToHex(chunkHash));
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(chunkFile))) {
            os.write(chunkData);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Đọc chunk từ disk
    public byte[] loadChunk(byte[] chunkHash) {
        Path chunkFile = baseDir.resolve(bytesToHex(chunkHash));
        if (!Files.exists(chunkFile)) return null;
        try {
            return Files.readAllBytes(chunkFile);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Kiểm tra chunk tồn tại
    public boolean hasChunk(byte[] chunkHash) {
        return Files.exists(baseDir.resolve(bytesToHex(chunkHash)));
    }

    // Xóa chunk
    public boolean deleteChunk(byte[] chunkHash) {
        Path chunkFile = baseDir.resolve(bytesToHex(chunkHash));
        try {
            return Files.deleteIfExists(chunkFile);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Tính hash SHA-1 của chunk
    public static byte[] sha1(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return md.digest(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Chuyển byte[] → hex string
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // Chuyển hex string → byte[]
    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
}
