package bittorrent;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class MetadataStore {
    private final Map<String, TorrentFile> metadataMap = new ConcurrentHashMap<>();
    private final String metadataDir = "./metadata";

    public MetadataStore() {
        new File(metadataDir).mkdirs();
        loadAllMetadata();

        // Hook để tự động lưu khi JVM shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🔄 JVM shutdown detected: saving all metadata...");
            saveAllMetadata();
        }));
    }

    // Lưu metadata vào bộ nhớ và file
    public void storeMetadata(TorrentFile torrent) {
        synchronized (this) {
            metadataMap.put(torrent.getInfoHash(), torrent);
            saveMetadataToFile(torrent);
            System.out.println("Stored metadata for: " + torrent.getInfoHash());
        }
    }

    // Lấy metadata từ bộ nhớ
    public TorrentFile getMetadata(String infoHash) {
        return metadataMap.get(infoHash); // ConcurrentHashMap đọc không cần synchronized
    }

    public boolean hasMetadata(String infoHash) {
        return metadataMap.containsKey(infoHash);
    }

    // Xóa metadata khỏi bộ nhớ và file
    public void removeMetadata(String infoHash) {
        synchronized (this) {
            metadataMap.remove(infoHash);
            File file = new File(metadataDir, infoHash + ".meta");
            if (file.exists() && !file.delete()) {
                System.err.println("Failed to delete metadata file: " + file.getName());
            } else {
                System.out.println("Deleted metadata file: " + file.getName());
            }
        }
    }

    // Load tất cả metadata từ thư mục
    private void loadAllMetadata() {
        File dir = new File(metadataDir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".meta"));

        if (files == null) return;

        for (File file : files) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                TorrentFile torrent = (TorrentFile) ois.readObject();
                metadataMap.put(torrent.getInfoHash(), torrent);
                System.out.println("✓ Loaded metadata: " + torrent.getFileName());
            } catch (Exception e) {
                System.err.println("Failed to load " + file.getName() + ": " + e.getMessage());
            }
        }

        System.out.println("✓ Loaded " + metadataMap.size() + " metadata files");
    }

    // Ghi metadata ra file
    private void saveMetadataToFile(TorrentFile torrent) {
        File file = new File(metadataDir, torrent.getInfoHash() + ".meta");
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(torrent);
        } catch (IOException e) {
            System.err.println("Failed to save metadata for " + torrent.getFileName() + ": " + e.getMessage());
        }
    }

    // Lưu tất cả metadata ra file (dùng cho JVM shutdown)
    private void saveAllMetadata() {
        synchronized (this) {
            for (TorrentFile torrent : metadataMap.values()) {
                saveMetadataToFile(torrent);
            }
            System.out.println("✓ All metadata saved.");
        }
    }
}
