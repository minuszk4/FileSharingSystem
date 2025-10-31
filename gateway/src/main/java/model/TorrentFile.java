package model;

import util.HashUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TorrentFile implements Serializable {
    private String fileName;
    private long fileSize;
    private int pieceLength; // e.g., 256 KB
    private List<String> pieces; // SHA-1 hash mỗi piece, dạng hex
    private String infoHash; // SHA-1 tổng thể

    public TorrentFile(String fileName, long fileSize, int pieceLength) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.pieceLength = pieceLength;
        this.pieces = new ArrayList<>();
    }

    // Thêm hash piece
    public void addPieceHash(byte[] pieceData) {
        String hashHex = HashUtil.sha1(pieceData); // trả về hex string
        pieces.add(hashHex);
    }

    // Tạo infoHash tổng thể từ fileName + fileSize + piece hashes
    public void generateInfoHash() {
        StringBuilder sb = new StringBuilder();
        sb.append(fileName).append(fileSize);
        for (String pieceHash : pieces) {
            sb.append(pieceHash);
        }
        this.infoHash = HashUtil.sha1(sb.toString().getBytes());
    }

    // Getters
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public int getPieceLength() { return pieceLength; }
    public List<String> getPieces() { return pieces; }
    public String getInfoHash() { return infoHash; }
    public void setInfoHash(String infoHash) {
        this.infoHash = infoHash;
    }
    @Override
    public String toString() {
        return "TorrentFile{" +
                "fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", pieceLength=" + pieceLength +
                ", pieces=" + pieces.size() + " pieces" +
                ", infoHash='" + infoHash + '\'' +
                '}';
    }
}
