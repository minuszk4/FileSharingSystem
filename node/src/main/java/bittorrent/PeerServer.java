package bittorrent;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class PeerServer {
    private final int port;
    private final PieceManager pieceManager;
    private final MetadataStore metadataStore;
    private final ExecutorService executor = Executors.newFixedThreadPool(20);
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public PeerServer(int port, PieceManager pieceManager, MetadataStore metadataStore) {
        this.port = port;
        this.pieceManager = pieceManager;
        this.metadataStore = metadataStore;
    }

//    public void start() throws IOException {
//        serverSocket = new ServerSocket(port);
//        running = true;
//
//        System.out.println("PeerServer started on port " + port);
//
//        executor.submit(() -> {
//            while (running) {
//                try {
//                    Socket clientSocket = serverSocket.accept();
//                    executor.submit(() -> handleClient(clientSocket));
//                } catch (IOException e) {
//                    if (running) {
//                        System.err.println("Error accepting connection: " + e.getMessage());
//                    }
//                }
//            }
//        });
//    }

//    private void handleClient(Socket socket) {
//        try (DataInputStream in = new DataInputStream(socket.getInputStream());
//             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
//
//            byte messageType = in.readByte();
//
//            switch (messageType) {
//                case 0x01: // MSG_REQUEST_PIECE
//                    handlePieceRequest(in, out);
//                    break;
//
//                case 0x02: // MSG_REQUEST_METADATA
//                    handleMetadataRequest(in, out);
//                    break;
//
//                default:
//                    System.err.println("Unknown message type: " + messageType);
//            }
//
//        } catch (IOException e) {
//            System.err.println("Error handling client: " + e.getMessage());
//        } finally {
//            try {
//                socket.close();
//            } catch (IOException e) {
//                // ignore
//            }
//        }
//    }
//
//    private void handlePieceRequest(DataInputStream in, DataOutputStream out) throws IOException {
//        String infoHash = in.readUTF();
//        int pieceIndex = in.readInt();
//
//        byte[] piece = pieceManager.getPiece(infoHash, pieceIndex);
//
//        if (piece != null) {
//            out.writeInt(piece.length);
//            out.write(piece);
//            out.flush();
//        } else {
//            out.writeInt(0); // No data
//            out.flush();
//        }
//    }
//
//    private void handleMetadataRequest(DataInputStream in, DataOutputStream out) throws IOException {
//        String infoHash = in.readUTF();
//
//        TorrentFile torrent = metadataStore.getMetadata(infoHash);
//
//        if (torrent == null) {
//            out.writeByte(0x04); // MSG_METADATA_REJECT
//            out.flush();
//            return;
//        }
//
//        // Send metadata
//        out.writeByte(0x03); // MSG_METADATA_RESPONSE
//        out.writeUTF(torrent.getFileName());
//        out.writeLong(torrent.getFileSize());
//        out.writeInt(torrent.getPieceLength());
//        out.writeInt(torrent.getPieces().size());
//
//        // Send all piece hashes
//        for (String pieceHash : torrent.getPieces()) {
//            out.writeUTF(pieceHash);
//        }
//
//        out.flush();
//        System.out.println("✓ Sent metadata for " + infoHash + " to peer");
//    }
//
//    public void stop() {
//        running = false;
//        try {
//            if (serverSocket != null) {
//                serverSocket.close();
//            }
//            executor.shutdown();
//        } catch (IOException e) {
//            System.err.println("Error stopping server: " + e.getMessage());
//        }
//    }
}