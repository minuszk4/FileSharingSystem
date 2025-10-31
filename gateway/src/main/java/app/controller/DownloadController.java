package app.controller;// gateway/src/main/java/com/dfss/gateway/controller/DownloadController.java

import jakarta.annotation.PostConstruct;
import model.TorrentFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("download")
public class DownloadController {

    @Value("${nodes}")
    private String nodesConfig;

    private List<String> nodeUrls;
    private RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void init() {
        this.nodeUrls = Arrays.asList(nodesConfig.split(","));
    }

    /**
     * Gateway chỉ forward request đến node và stream response về client
     */
    @GetMapping(value = "/magnet", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadMagnet(
            @RequestParam("hash") String infoHash) {

        // Chọn node đầu tiên có metadata
        String nodeUrl = findNodeWithMetadata(infoHash);

        if (nodeUrl == null) {
            return ResponseEntity.notFound().build();
        }

        System.out.println("[Gateway] Proxying download to: " + nodeUrl);

        StreamingResponseBody stream = outputStream -> {
            try {
                // Forward request đến node và stream response về client
                String url = nodeUrl + "/node/download/stream?hash=" + infoHash;

                restTemplate.execute(url, HttpMethod.GET, null, clientHttpResponse -> {
                    // Copy headers từ node response
                    String contentDisposition = clientHttpResponse.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);

                    // Stream data từ node về client
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = clientHttpResponse.getBody().read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        outputStream.flush();
                    }
                    return null;
                });

            } catch (Exception e) {
                System.err.println("❌ [Gateway] Proxy failed: " + e.getMessage());
                throw new IOException("Download failed", e);
            }
        };

        // Lấy metadata để set filename trong header
        TorrentFile metadata = getMetadataFromNode(nodeUrl, infoHash);
        String filename = metadata != null ? metadata.getFileName() : infoHash + ".file";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(stream);
    }

    private String findNodeWithMetadata(String infoHash) {
        for (String nodeUrl : nodeUrls) {
            try {
                ResponseEntity<TorrentFile> response = restTemplate.getForEntity(
                        nodeUrl + "/node/metadata?hash=" + infoHash,
                        TorrentFile.class
                );
                if (response.getStatusCode().is2xxSuccessful()) {
                    return nodeUrl;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private TorrentFile getMetadataFromNode(String nodeUrl, String infoHash) {
        try {
            ResponseEntity<TorrentFile> response = restTemplate.getForEntity(
                    nodeUrl + "/node/metadata?hash=" + infoHash,
                    TorrentFile.class
            );
            return response.getBody();
        } catch (Exception e) {
            return null;
        }
    }
}