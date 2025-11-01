package bittorrent;

import java.net.*;
import java.io.*;

public class PeerConnection {
    private final String ip;
    private final int port;
    private MetadataStore metadataStore;
    private static final byte MSG_REQUEST_PIECE = 0x01;
    private static final byte MSG_REQUEST_METADATA = 0x02;
    private static final byte MSG_METADATA_RESPONSE = 0x03;
    private static final byte MSG_METADATA_REJECT = 0x04;

    public PeerConnection(String ip, int port,MetadataStore metadataStore) {
        this.ip = ip;
        this.port = port;
        this.metadataStore = metadataStore;
    }

//    // Request piece từ peer
//    public byte[] requestPiece(String infoHash, int pieceIndex) {
//        try (Socket socket = new Socket(ip, port);
//             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
//             DataInputStream in = new DataInputStream(socket.getInputStream())) {
//
//            // Send request
//            out.writeByte(MSG_REQUEST_PIECE);
//            out.writeUTF(infoHash);
//            out.writeInt(pieceIndex);
//            out.flush();
//
//            // Receive response
//            int len = in.readInt();
//            byte[] data = new byte[len];
//            in.readFully(data);
//            return data;
//
//        } catch (IOException e) {
//            e.printStackTrace();
//            return null;
//        }
//    }
//    public TorrentFile requestMetadata(String infoHash) {
//        if (metadataStore.hasMetadata(infoHash)) {
//            System.out.println("✓ Using cached metadata for " + infoHash);
//            return metadataStore.getMetadata(infoHash);
//        }
//        try (Socket socket = new Socket(ip, port);
//             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
//             DataInputStream in = new DataInputStream(socket.getInputStream())) {
//
//            // Send metadata request
//            out.writeByte(MSG_REQUEST_METADATA);
//            out.writeUTF(infoHash);
//            out.flush();
//
//            // Read response type
//            byte responseType = in.readByte();
//
//            if (responseType == MSG_METADATA_REJECT) {
//                System.err.println("Peer rejected metadata request");
//                return null;
//            }
//
//            if (responseType != MSG_METADATA_RESPONSE) {
//                System.err.println("Unexpected response type: " + responseType);
//                return null;
//            }
//
//            // Read metadata
//            String fileName = in.readUTF();
//            long fileSize = in.readLong();
//            int pieceLength = in.readInt();
//            int numPieces = in.readInt();
//
//            TorrentFile torrent = new TorrentFile(fileName, fileSize, pieceLength);
//            torrent.setInfoHash(infoHash);
//
//            // Read all piece hashes
//            for (int i = 0; i < numPieces; i++) {
//                String pieceHash = in.readUTF();
//                torrent.getPieces().add(pieceHash);
//            }
//            metadataStore.storeMetadata(torrent);
//            System.out.println("✓ Received metadata from " + ip + ":" + port +
//                    " - " + fileName + " (" + numPieces + " pieces)");
//            return torrent;
//
//        } catch (IOException e) {
//            System.err.println("Failed to request metadata from " + ip + ":" + port + ": " + e.getMessage());
//            return null;
//        }
//    }
//
//    // Giả lập thông tin tổng số piece
//    public int getTotalPieces(String infoHash) {
//        // Placeholder: có thể gửi message query peer
//        return 10;
//    }
}
