package  controller;
import core.Contact;
import dht.KademliaNode;
import dht.RoutingTable;
import bittorrent.*;
import file.ChunkStorage;
import file.FileManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
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
            return ResponseEntity.ok(torrent.getInfoHash());
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
        }
    }

    @GetMapping("/find")
    public String findPeers(@RequestParam("hash") String infoHash) {
        torrentManager.findPeers(infoHash);
        return "Searching peers for infoHash: " + infoHash;
    }
    @PostMapping("/download/magnet")
    public ResponseEntity<String> downloadMagnet(@RequestParam("hash") String infoHash) {
        try {
            torrentManager.downloadFromMagnet(infoHash, "downloads");
            return ResponseEntity.ok("Download completed for " + infoHash);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Download failed: " + e.getMessage());
        }
    }
    @GetMapping("/nodes")
    public ResponseEntity<List<String>> getKnownNodes() {
        List<String> knownNodes = node.getRoutingTable().getAllContacts().stream()
                .map(c -> c.getIp() + ":" + c.getPort()+":"+c.getHttpPort())
                .toList();
        return ResponseEntity.ok(knownNodes);
    }



}
