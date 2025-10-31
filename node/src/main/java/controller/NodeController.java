package  controller;
import core.Contact;
import dht.KademliaNode;
import dht.RoutingTable;
import bittorrent.*;
import file.ChunkStorage;
import file.FileManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/node")
public class NodeController {

    @Value("${NODE_PORT}")
    private int nodePort;

    @Value("${PEER_PORT:0}") // Default: auto-calculate
    private int peerPort;

    @Value("${BOOTSTRAP_NODE:}")
    private String bootstrapNode;

    private KademliaNode node;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private TorrentManager torrentManager;
    private PieceManager pieceManager;

    @PostConstruct
    public void init() {
        try {
            node = new KademliaNode(nodePort);
            System.out.println("Khởi tạo tại port: "+nodePort);
            node.start();
            FileManager fileManager = new FileManager();
            PieceManager pieceManager = new PieceManager(fileManager,1024*1024);
            MetadataStore metadataStore = new MetadataStore();
            node.startPeerServer(pieceManager, metadataStore);
            torrentManager = new TorrentManager(pieceManager, node,metadataStore);

            if (bootstrapNode != null && !bootstrapNode.isEmpty()) {
                String[] parts = bootstrapNode.split(":");
                node.bootstrap(parts[0], Integer.parseInt(parts[1]));
            }

            System.out.println("Node started on port " + nodePort +
                    (bootstrapNode.isEmpty() ? " (bootstrap node)" : " (bootstrapped to " + bootstrapNode + ")"));
            startNetworkMonitor();
        } catch (Exception e) {
            throw new RuntimeException(" Failed to start node", e);
        }
    }
    private void startNetworkMonitor() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                RoutingTable knownNodes = node.getRoutingTable();
                System.out.println(knownNodes);

            } catch (Exception e) {
                System.err.println(" Error monitoring node " + nodePort + ": " + e.getMessage());
            }
        }, 5, 10, TimeUnit.SECONDS); // delay 5s, log mỗi 10s
    }

    // Upload
    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            File uploadDir = new File(System.getenv("DATA_PATH") + "/uploads");
            if (!uploadDir.exists()) uploadDir.mkdirs();

            File tempFile = new File(uploadDir, file.getOriginalFilename());
            file.transferTo(tempFile);

            TorrentFile torrent = torrentManager.seedFile(tempFile);
            if (torrent == null) {
                return ResponseEntity.status(500).body("Failed to seed file");
            }
            System.out.println(torrent.getInfoHash());
            return ResponseEntity.ok(torrent.getInfoHash());
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
        }
    }

    @GetMapping("/findPeers")
    public String findPeers(@RequestParam("hash") String infoHash) {
        torrentManager.findPeers(infoHash);
        return "Searching peers for infoHash: " + infoHash;
    }



    @GetMapping(value = "/piece", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getPiece(
            @RequestParam("hash") String infoHash,
            @RequestParam("index") int pieceIndex) {

        try {
            System.out.println("[Node] Request piece " + pieceIndex + " for " + infoHash);

            // 1. Kiểm tra local storage trước
            byte[] piece = pieceManager.getPiece(infoHash, pieceIndex);

            // 2. Nếu không có local, query từ DHT
            if (piece == null) {
                String pieceKey = infoHash + ":" + pieceIndex;
                piece = node.retrievePiece(pieceKey);

                // Cache lại piece vừa lấy được
                if (piece != null) {
                    pieceManager.savePiece(infoHash, pieceIndex, piece);
                }
            }

            if (piece != null) {
                System.out.println("✅ [Node] Returned piece " + pieceIndex);
                return ResponseEntity.ok()
                        .header("X-Piece-Index", String.valueOf(pieceIndex))
                        .header("X-Piece-Size", String.valueOf(piece.length))
                        .body(piece);
            } else {
                System.err.println("❌ [Node] Piece " + pieceIndex + " not found");
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            System.err.println("❌ [Node] Error getting piece " + pieceIndex + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * API trả metadata của torrent
     */
    @GetMapping("/metadata")
    public ResponseEntity<TorrentFile> getMetadata(@RequestParam("hash") String infoHash) {
        try {
            TorrentFile metadata = node.getMetadataStore().getMetadata(infoHash);
            if (metadata != null) {
                return ResponseEntity.ok(metadata);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/nodes")
    public ResponseEntity<List<String>> getKnownNodes() {
        List<String> knownNodes = node.getRoutingTable().getAllContacts().stream()
                .map(c -> c.getIp() + ":" + c.getPort()+":"+c.getHttpPort())
                .toList();
        return ResponseEntity.ok(knownNodes);
    }

    @GetMapping(value = "/download/stream", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadAndStream(
            @RequestParam("hash") String infoHash) {

        System.out.println("[Node] Starting streaming download for: " + infoHash);

        StreamingResponseBody stream = outputStream -> {
            try {
                // Gọi method streaming của TorrentManager
                torrentManager.downloadAndStream(infoHash, outputStream);
            } catch (Exception e) {
                System.err.println("❌ [Node] Streaming failed: " + e.getMessage());
                throw new IOException("Download failed", e);
            }
        };

        // Lấy metadata để set filename
        TorrentFile metadata = torrentManager.fetchMetadata(infoHash);
        String filename = metadata != null ? metadata.getFileName() : infoHash + ".file";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(stream);
    }

    @GetMapping("/find")
    public ResponseEntity<?> findFile(@RequestParam("hash") String infoHash) {
        try {
            System.out.println("🔍 [Node] FIND file with hash: " + infoHash);

            // 1️⃣ Tìm metadata trong local
            TorrentFile metadata = node.getMetadataStore().getMetadata(infoHash);

            // 2️⃣ Nếu không có, thử tìm trong DHT (qua torrentManager)
            if (metadata == null) {
                System.out.println("⏳ Metadata not found locally, searching via DHT...");
                metadata = torrentManager.fetchMetadata(infoHash);
            }

            // 3️⃣ Nếu vẫn không có => không tìm thấy
            if (metadata == null) {
                System.out.println("❌ [Node] File not found in DHT");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "File not found for hash: " + infoHash));
            }

            // 4️⃣ Trả thông tin file
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("name", metadata.getFileName());
            fileInfo.put("size", metadata.getFileSize());
            fileInfo.put("hash", metadata.getInfoHash());

            System.out.println("✅ [Node] Found file: " + metadata.getFileName());
            return ResponseEntity.ok(fileInfo);

        } catch (Exception e) {
            System.err.println("❌ [Node] Error while finding file: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

}
