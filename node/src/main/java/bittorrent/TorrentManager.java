package bittorrent;

import core.Contact;
import dht.KademliaNode;
import util.HashUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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
            System.out.println("[seedFile] Start seeding file: " + file.getName());

            // 1. Tạo TorrentFile và chia pieces
            TorrentFile torrent = new TorrentFile(file.getName(), file.length(), 256 * 1024);
            List<byte[]> pieces = pieceManager.splitFile(file);

            System.out.println("[seedFile] File split into " + pieces.size() + " pieces");

            // 2. Generate piece hashes
            for (byte[] pieceData : pieces) {
                torrent.addPieceHash(pieceData);
            }
            torrent.generateInfoHash();
            String infoHash = torrent.getInfoHash();

            System.out.println("[seedFile] Generated infoHash: " + infoHash);

            // 3. Lưu metadata local
            metadataStore.storeMetadata(torrent);

            // 4. PHÂN TÁN PIECES THEO DHT ROUTING
            distributeViaDHT(infoHash, pieces);

            // 5. Announce file lên DHT
            dhtNode.storePeer(infoHash);
            // store metadata
            dhtNode.storeMetadataToDHT(torrent);

            System.out.println("[seedFile] Finished seeding file: " + file.getName());
            return torrent;

        } catch (Exception e) {
            System.err.println("[seedFile] Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    private void distributeViaDHT(String infoHash, List<byte[]> pieces) {
        System.out.println("[distributeViaDHT] Distributing " + pieces.size() + " pieces via DHT");

        for (int i = 0; i < pieces.size(); i++) {
            final int pieceIndex = i;
            final byte[] pieceData = pieces.get(i);

            executor.submit(() -> {
                try {
                    String pieceKey = infoHash + ":" + pieceIndex;


                    dhtNode.storePiece(pieceKey, pieceData);

                    System.out.println("✅ Piece " + pieceIndex + " distributed via DHT");

                } catch (Exception e) {
                    System.err.println("❌ Failed to distribute piece " + pieceIndex + ": " + e.getMessage());
                }
            });
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
            peers.add(new PeerConnection(c.getIp(), c.getPort(),metadataStore));
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
        int totalPieces = torrent.getPieces().size();

        System.out.println("[download] Starting download: " + torrent.getFileName());
        System.out.println("[download] Total pieces: " + totalPieces);

        CountDownLatch latch = new CountDownLatch(totalPieces);

        // Download pieces song song
        for (int i = 0; i < totalPieces; i++) {
            final int pieceIndex = i;
            executor.submit(() -> {
                try {
                    String pieceKey = infoHash + ":" + pieceIndex;

                    // Lấy piece từ DHT (DHT tự tìm node có piece)
                    byte[] piece = dhtNode.retrievePiece(pieceKey);

                    if (piece != null) {
                        // Verify hash
                        String hash = util.HashUtil.sha1(piece);
                        String expectedHash = torrent.getPieces().get(pieceIndex);

                        if (hash.equals(expectedHash)) {
                            pieceManager.savePiece(infoHash, pieceIndex, piece);
                            System.out.println("✅ Downloaded piece " + pieceIndex);
                        } else {
                            System.err.println("❌ Hash mismatch for piece " + pieceIndex);
                        }
                    } else {
                        System.err.println("❌ Failed to download piece " + pieceIndex);
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error downloading piece " + pieceIndex + ": " + e.getMessage());
                }
                latch.countDown();
            });
        }

        latch.await();

        // Reassemble file
        reassembleFile(infoHash, torrent, outputDir);
    }

    private void reassembleFile(String infoHash, TorrentFile torrent, String outputDir) {
        File outputFile = new File(outputDir, torrent.getFileName());

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            for (int i = 0; i < torrent.getPieces().size(); i++) {
                byte[] piece = pieceManager.getPiece(infoHash, i);
                if (piece != null) {
                    fos.write(piece);
                }
            }
            System.out.println("✅ File assembled: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Download completed: " + outputFile.getAbsolutePath());
    }

    public void downloadAndStream(String infoHash, OutputStream outputStream) throws Exception {
        System.out.println("[TorrentManager] Starting streaming download: " + infoHash);

        // 1. Lấy metadata
        TorrentFile torrent = fetchMetadata(infoHash);
        if (torrent == null) {
            throw new RuntimeException("Cannot fetch metadata for: " + infoHash);
        }

        int totalPieces = torrent.getPieces().size();
        System.out.println("[TorrentManager] Total pieces: " + totalPieces);

        // 2. Download pieces song song
        ConcurrentHashMap<Integer, byte[]> downloadedPieces = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(totalPieces);
        AtomicInteger downloadedCount = new AtomicInteger(0);

        for (int i = 0; i < totalPieces; i++) {
            final int pieceIndex = i;
            executor.submit(() -> {
                try {
                    String pieceKey = infoHash + ":" + pieceIndex;

                    // Lấy piece từ DHT
                    byte[] piece = dhtNode.retrievePiece(pieceKey);

                    if (piece != null) {
                        // Verify hash
                        String actualHash = HashUtil.sha1(piece);
                        String expectedHash = torrent.getPieces().get(pieceIndex);

                        if (actualHash.equals(expectedHash)) {
                            downloadedPieces.put(pieceIndex, piece);
                            int count = downloadedCount.incrementAndGet();
                            System.out.println("✅ Downloaded piece " + pieceIndex
                                    + " (" + count + "/" + totalPieces + ")");
                        } else {
                            System.err.println("❌ Hash mismatch for piece " + pieceIndex);
                        }
                    } else {
                        System.err.println("❌ Failed to download piece " + pieceIndex);
                    }

                } catch (Exception e) {
                    System.err.println("❌ Error downloading piece " + pieceIndex + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 3. Stream pieces theo thứ tự về client
        System.out.println("[TorrentManager] Streaming pieces to client...");
        BufferedOutputStream bufferedOutput = new BufferedOutputStream(outputStream, 65536);

        for (int i = 0; i < totalPieces; i++) {
            // Đợi piece i
            byte[] piece = null;
            int attempts = 0;
            while (piece == null && attempts < 300) { // 30s timeout
                piece = downloadedPieces.get(i);
                if (piece == null) {
                    Thread.sleep(100);
                    attempts++;
                }
            }

            if (piece == null) {
                throw new RuntimeException("Timeout waiting for piece " + i);
            }

            // Stream ngay lập tức
            bufferedOutput.write(piece);
            bufferedOutput.flush();
            System.out.println("📤 Streamed piece " + i + " (" + piece.length + " bytes)");
        }

        bufferedOutput.flush();
        latch.await(5, TimeUnit.MINUTES);
        System.out.println("✅ [TorrentManager] Streaming completed");
    }


}
