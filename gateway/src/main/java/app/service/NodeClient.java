package app.service;

import model.Contact;
import model.MultipartInputStreamFileResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import model.FileInfo;
import java.io.IOException;

@Component
public class NodeClient {

    private final RestTemplate rest;

    public NodeClient(RestTemplate rest) {
        this.rest = rest;
    }

    public String uploadFile(Contact node, MultipartFile file) throws IOException {
        String url = "http://" + node.getIp() + ":" + node.getHttpPort() + "/node/upload";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new MultipartInputStreamFileResource(
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getSize()
        ));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = rest.postForEntity(url, requestEntity, String.class);

        System.out.println("✅ Upload response: " + response.getBody());
        return response.getBody();
    }
    public FileInfo findFile(Contact node, String hash) {
        String url = "http://" + node.getIp() + ":" + node.getHttpPort() + "/node/find?hash=" + hash;

        try {
            ResponseEntity<FileInfo> response = rest.getForEntity(url, FileInfo.class);
            return response.getBody();
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi tìm file từ node " + node.getIp() + ":" + node.getHttpPort());
            e.printStackTrace();
            return null;
        }
    }

}


