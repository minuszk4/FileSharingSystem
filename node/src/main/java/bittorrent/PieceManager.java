package bittorrent;

import file.FileManager;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class PieceManager {
    private final FileManager fileManager;
    private final Map<String, List<byte[]>> pieces; // infoHash -> piece data list
    private final int pieceSize;

    public PieceManager(FileManager fileManager, int pieceSize) {
        this.fileManager = fileManager;
        this.pieces = new ConcurrentHashMap<>();
        this.pieceSize = pieceSize;
    }

    // Chia file, lưu piece vào map và trả list hash để tạo TorrentFile
    public List<byte[]> loadFile(String infoHash, File file) throws IOException {
        System.out.println("[loadFile] Start loading file: " + file.getAbsolutePath());

        List<byte[]> chunkHashes = fileManager.splitFile(file); // trả về list hash

        if (chunkHashes == null || chunkHashes.isEmpty()) {
            System.out.println("[loadFile] WARNING: No chunks returned for file " + file.getName());
        } else {
            System.out.println("[loadFile] Total chunks: " + chunkHashes.size());
            for (int i = 0; i < chunkHashes.size(); i++) {
                System.out.println("[loadFile] Chunk " + i + " hash: " + bytesToHex(chunkHashes.get(i)));
            }
        }

        pieces.put(infoHash, chunkHashes);
        System.out.println("[loadFile] Finished loading file with infoHash: " + infoHash);

        return chunkHashes;
    }
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    public byte[] getPiece(String infoHash, int index) {
        List<byte[]> list = pieces.get(infoHash);
        if(list == null || index >= list.size()) return null;
        return list.get(index);
    }

    public void savePiece(String infoHash, int index, byte[] data) {
        pieces.computeIfAbsent(infoHash, k -> new ArrayList<>());
        List<byte[]> list = pieces.get(infoHash);
        while(list.size() <= index) list.add(null);
        list.set(index, data);
        System.out.println("Saved piece " + index + " for torrent " + infoHash);
    }

    public int getTotalPieces(String infoHash) {
        List<byte[]> list = pieces.get(infoHash);
        return list == null ? 0 : list.size();
    }
}
