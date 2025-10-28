package bittorrent;

import core.Contact;
import dht.KademliaNode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.*;

public class TorrentManager {
    private final PieceManager pieceManager;
    private final KademliaNode dhtNode;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    private final Map<String, List<PeerConnection>> peerMap = new ConcurrentHashMap<>();
    private final MetadataStore metadataStore;
    public TorrentManager(PieceManager pieceManager, KademliaNode dhtNode, MetadataStore metadataStore) {
        this.pieceManager = pieceManager;
        this.dhtNode = dhtNode;
        this.metadataStore = metadataStore;
    }

    public TorrentFile seedFile(File file) {
        try {
            System.out.println("[seedFile] Start seeding file: " + file.getAbsolutePath() + " (size=" + file.length() + ")");

            // 1. Tạo TorrentFile
            TorrentFile torrent = new TorrentFile(file.getName(), file.length(), 256 * 1024);
            System.out.println("[seedFile] TorrentFile object created for: " + file.getName());

            // 2. Chia file thành pieces và lưu vào PieceManager
            List<byte[]> chunkHashes = pieceManager.loadFile(file.getName(), file);
            System.out.println("[seedFile] File split into " + chunkHashes.size() + " pieces");

            // 3. Lấy hash từng piece
            int totalPieces = pieceManager.getTotalPieces(file.getName());
            System.out.println("[seedFile] Total pieces according to PieceManager: " + totalPieces);
            for (int i = 0; i < totalPieces; i++) {
                byte[] piece = pieceManager.getPiece(file.getName(), i);
                torrent.addPieceHash(piece);
                System.out.println("[seedFile] Added piece " + i + " hash: " + bytesToHex(piece));
            }

            // 4. Tạo infoHash tổng thể
            torrent.generateInfoHash();
            System.out.println("[seedFile] Generated infoHash: " + torrent.getInfoHash());

            // 5. Lưu metadata vào store
            metadataStore.storeMetadata(torrent);
            System.out.println("[seedFile] Metadata stored for file: " + file.getName());

            // 6. Announce self lên DHT
            dhtNode.storePeer(torrent.getInfoHash());
            System.out.println("[seedFile] Announced file on DHT: " + torrent.getInfoHash());

            System.out.println("[seedFile] Finished seeding file: " + file.getName());
            return torrent;

        } catch (Exception e) {
            System.err.println("[seedFile] Error seeding file: " + file.getName());
            e.printStackTrace();
            return null;
        }
    }

    // Hàm tiện ích để log byte[] dưới dạng hex
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }


    // ================== FIND PEERS ==================
    public void findPeers(String infoHash) {
        List<Contact> contacts = dhtNode.findPeers(infoHash);
        List<PeerConnection> peers = new ArrayList<>();
        for (Contact c : contacts) {
            peers.add(new PeerConnection(c.getIp(), c.getPort()));
        }
        peerMap.put(infoHash, peers);
        System.out.println("Found " + peers.size() + " peers for infoHash: " + infoHash);
    }
    // Fetch metadata
    public TorrentFile fetchMetadata(String infoHash) {
        if(metadataStore.hasMetadata(infoHash)) {
            System.out.println("Found metadata for infoHash: " + infoHash);
            return metadataStore.getMetadata(infoHash);
        }
        findPeers(infoHash);
        List<PeerConnection> peers = peerMap.get(infoHash);
        if(peers.size() == 0) {
            System.out.println("No peers found for infoHash: " + infoHash);
            return null;
        }
        for(PeerConnection peer : peers) {
            TorrentFile torent = peer.requestMetadata(infoHash);
            if(torent != null) {
                metadataStore.storeMetadata(torent);
                return torent;
            }
        }
        System.out.println("Failed to find metadata for infoHash: " + infoHash);
        return null;
    }
    // DOWNLOAD FILE
    public void downloadTorrent(TorrentFile torrent, String outputDir) throws InterruptedException {
        String infoHash = torrent.getInfoHash();
        List<PeerConnection> peers = peerMap.get(infoHash);
        if (peers == null || peers.isEmpty()) {
            System.out.println("No peers for torrent " + infoHash);
            return;
        }

        int totalPieces = torrent.getPieces().size();
        CountDownLatch latch = new CountDownLatch(totalPieces);

        for (int i = 0; i < totalPieces; i++) {
            final int idx = i;
            executor.submit(() -> {
                boolean downloaded = false;
                for (PeerConnection peer : peers) {
                    byte[] piece = peer.requestPiece(infoHash, idx);
                    if (piece != null) {
                        // Verify hash
                        String hash = util.HashUtil.sha1(piece);
                        if (hash.equals(torrent.getPieces().get(idx))) {
                            pieceManager.savePiece(infoHash, idx, piece);
                            downloaded = true;
                            break;
                        }
                    }
                }
                if (!downloaded) {
                    System.err.println("Failed to download/verify piece " + idx);
                }
                latch.countDown();
            });
        }

        latch.await();

        // Ghép file lại
        File outputFile = new File(outputDir, "downloaded_" + torrent.getFileName());
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            for (int i = 0; i < totalPieces; i++) {
                byte[] piece = pieceManager.getPiece(infoHash, i);
                if (piece != null) fos.write(piece);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Download completed: " + outputFile.getAbsolutePath());
    }
    public void downloadFromMagnet(String infoHash,String outputDir) throws InterruptedException {
        System.out.println("Downloading from magnet: " + infoHash);
        TorrentFile torrent = fetchMetadata(infoHash);
        if(torrent == null) {
            throw new RuntimeException("Failed to find metadata for infoHash: " + infoHash);
        }
        downloadTorrent(torrent,outputDir);
    }
}
