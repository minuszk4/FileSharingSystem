package bittorrent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class MetadataStore {
    private final Map<String, TorrentFile> metadataMap = new ConcurrentHashMap<>();

    public void storeMetadata(TorrentFile torrent) {
        metadataMap.put(torrent.getInfoHash(), torrent);
        System.out.println("Stored metadata for: " + torrent.getInfoHash());
    }

    public TorrentFile getMetadata(String infoHash) {
        return metadataMap.get(infoHash);
    }

    public boolean hasMetadata(String infoHash) {
        return metadataMap.containsKey(infoHash);
    }

    public void removeMetadata(String infoHash) {
        metadataMap.remove(infoHash);
    }
}