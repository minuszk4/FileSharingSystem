package app.service;

import model.Contact;
import model.FileInfo;
import model.NodeInfo;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import util.HashUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

@Service
public class GatewayService {

    @Value("${gateway.upload-dir}")
    private String uploadDir;

    private final NodeDiscoveryService nodeDiscoveryService;
    private final NodeClient nodeClient; // client để gửi file đến node

    public GatewayService(NodeDiscoveryService nodeDiscoveryService, NodeClient nodeClient) {
        this.nodeDiscoveryService = nodeDiscoveryService;
        this.nodeClient = nodeClient;
    }

    public FileInfo store(MultipartFile file) throws Exception {

        List<Contact> nodes = nodeDiscoveryService.listNodes();
        if (nodes.isEmpty()) {
            throw new RuntimeException("❌ Không có node nào khả dụng trong mạng DHT!");
        }
        Contact chosenNode = nodes.get(new Random().nextInt(nodes.size()));

        System.out.println("📦 Gửi file đến node: " + chosenNode.getIp()+":"+chosenNode.getHttpPort());

        nodeClient.uploadFile(chosenNode, file);

        return new FileInfo(file.getOriginalFilename(), file.getSize(), file.getName(), null);
    }

    public List<Contact> listNodes() {
        return nodeDiscoveryService.listNodes();
    }
}
