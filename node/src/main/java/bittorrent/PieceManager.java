package bittorrent;

import file.FileManager;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

public class PieceManager {
    private final FileManager fileManager;
    private final Map<String, List<byte[]>> pieces; // infoHash -> piece data list
    private final int pieceSize;
    private final String storageDir = "./data/pieces/";

    public PieceManager(FileManager fileManager, int pieceSize) {
        this.fileManager = fileManager;
        this.pieces = new ConcurrentHashMap<>();
        this.pieceSize = pieceSize;
    }

    public List<byte[]> loadFile(File file) throws IOException {
        System.out.println("[loadFile] Start loading file: " + file.getAbsolutePath());

        List<byte[]> chunkHashes = fileManager.splitFile(file); // trả về list hash
        return chunkHashes;
    }

    public byte[] loadPieceData(String pieceKey) throws IOException {
        String safeKey = pieceKey.replace(":", "_");
        File file = new File(storageDir, safeKey);

        if (!file.exists()) {
            return null;
        }

        return Files.readAllBytes(file.toPath());
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
    public void savePieceData(String pieceKey, byte[] data) throws IOException {
        File dir = new File(storageDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String safeKey = pieceKey.replace(":", "_");
        File file = new File(dir, safeKey);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }

        System.out.println("Saved: " + pieceKey + " (" + data.length + " bytes)");
    }
    public int getTotalPieces(String infoHash) {
        List<byte[]> list = pieces.get(infoHash);
        return list == null ? 0 : list.size();
    }
}
