package app.service;

import model.Contact;
import model.NodeID;
import model.NodeInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class NodeDiscoveryService {

    private RestTemplate rest;

    // Danh sách node trong mạng
    private final List<Contact> nodes = new ArrayList<>();
    public NodeDiscoveryService() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000); // hoặc timeoutMs nếu muốn dynamic
        requestFactory.setReadTimeout(3000);
        this.rest = new RestTemplate(requestFactory);
    }
    // Timeout cho ping (ms)
    @Value("${gateway.node-timeout-ms:3000}")
    private int timeoutMs;

    @PostConstruct
    public void init() throws InterruptedException, UnknownHostException, NoSuchAlgorithmException {
        String bootstrap = System.getenv("BOOTSTRAP_NODE");
        if (bootstrap == null || bootstrap.isEmpty()) {
            System.err.println("⚠️ BOOTSTRAP_NODE chưa set, DHT sẽ rỗng");
            return;
        }

        String[] parts = bootstrap.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        int httpPort = Integer.parseInt(parts[2]);

        NodeID nodeID = NodeID.fromHash(host + ":" + port);
        Contact bootstrapNode = new Contact(nodeID, InetAddress.getByName(host), port,httpPort);

        for (int i = 1; i <= 5; i++) { 
            try {
                System.out.println("🔄 Thử kết nối bootstrap node, lần " + i);
                List<Contact> discovered = fetchNodesFromBootstrap(bootstrapNode);
                if (!discovered.isEmpty()) {
                    nodes.add(bootstrapNode);
                    nodes.addAll(discovered);
                    System.out.println("✅ Kết nối thành công, tìm thấy " + nodes.size() + " node(s)");
                    break;
                } else {
                    System.out.println("⚠️ Chưa tìm thấy node nào, retry...");
                }
            } catch (Exception e) {
                System.err.println("❌ Lỗi khi fetch node từ bootstrap: " + e.getMessage());
            }
            Thread.sleep(2000);
        }

        if (nodes.isEmpty()) {
            System.err.println("⚠️ Không có node nào khả dụng trong mạng DHT sau 5 lần retry");
        }
    }


    /**
     * Lấy danh sách tất cả node đã biết
     */
    public List<Contact> getKnownNodes() {
        return nodes;
    }


    /**
     * Ping một node để kiểm tra trạng thái online/offline
     */
    public boolean ping(NodeInfo node) {
        try {
            String url = "http://" + node.getHost() + ":" + node.getPort() + "/node/status";
            rest.getForObject(url, String.class);
            node.setOnline(true);
            return true;
        } catch (Exception ex) {
            node.setOnline(false);
            return false;
        }
    }



    /**
     * Gửi request đến bootstrap node để lấy danh sách node khác
     * Giả sử bootstrap node expose API: GET /api/nodes
     */
    private List<Contact> fetchNodesFromBootstrap(Contact bootstrapNode) {
        try {
            String url = "http://" + bootstrapNode.getIp() + ":" + bootstrapNode.getPort() + "/node/nodes";
            ResponseEntity<List<String>> response = rest.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            List<String> nodeStrings = response.getBody();
            List<Contact> discovered = new ArrayList<>();
            if (nodeStrings != null) {
                for (String s : nodeStrings) {
                    String[] parts = s.split(":");
                    InetAddress ip = InetAddress.getByName(parts[0]);
                    int port = Integer.parseInt(parts[1]);
                    int httpPort = Integer.parseInt(parts[2]);
                    NodeID nodeID = NodeID.fromHash(parts[0] + ":" + parts[1]);
                    discovered.add(new Contact(nodeID, ip, port, httpPort));
                    System.out.println("Received node "+ip+":"+port);
                }
            }
            return discovered;
        } catch (Exception ex) {
            System.err.println("Failed to fetch nodes from bootstrap: " + ex.getMessage());
        }
        return new ArrayList<>();
    }


    public List<Contact> listNodes() {
        return nodes;
    }
}
