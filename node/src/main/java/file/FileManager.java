package file;

import java.io.*;
import java.util.*;

public class FileManager {
    private final ChunkStorage storage;
    private final int chunkSize; // bytes, ví dụ 256 KB
    public FileManager() {
        this.storage = new ChunkStorage(); // hoặc Storage in-memory mặc định
        this.chunkSize = 1024*256;
    }
    public FileManager(ChunkStorage storage, int chunkSize) {
        this.storage = storage;
        this.chunkSize = chunkSize;
    }

    // Chia file thành chunk và lưu
    public List<byte[]> splitFile(File file) throws IOException {
        List<byte[]> chunkHashes = new ArrayList<>();
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[chunkSize];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                byte[] chunkData = Arrays.copyOf(buffer, bytesRead);
                byte[] hash = ChunkStorage.sha1(chunkData);
                storage.saveChunk(chunkData, hash);
                chunkHashes.add(hash);
            }
        }
        return chunkHashes;
    }

    // Ghép file từ chunk list
    public boolean mergeFile(List<byte[]> chunkHashes, File outFile) {
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(outFile))) {
            for (byte[] hash : chunkHashes) {
                byte[] chunkData = storage.loadChunk(hash);
                if (chunkData == null) return false; // chunk missing
                os.write(chunkData);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Kiểm tra integrity của chunk
    public boolean verifyChunk(byte[] chunkHash) {
        byte[] data = storage.loadChunk(chunkHash);
        if (data == null) return false;
        byte[] hashCheck = ChunkStorage.sha1(data);
        return Arrays.equals(hashCheck, chunkHash);
    }

    // Kiểm tra integrity toàn bộ file
    public boolean verifyFile(List<byte[]> chunkHashes) {
        for (byte[] hash : chunkHashes) {
            if (!verifyChunk(hash)) return false;
        }
        return true;
    }
}
