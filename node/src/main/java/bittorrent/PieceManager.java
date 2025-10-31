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

    public List<byte[]> splitFile(File file) throws IOException {
        List<byte[]> pieces = new ArrayList<>();
        int pieceSize = 256 * 1024; // 256KB

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[pieceSize];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) > 0) {
                byte[] piece = new byte[bytesRead];
                System.arraycopy(buffer, 0, piece, 0, bytesRead);
                pieces.add(piece);
            }
        }

        return pieces;
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
