package app.service;

import model.Contact;
import model.MultipartInputStreamFileResource;
import model.NodeInfo;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Component
public class NodeClient {

    private final RestTemplate rest;

    public NodeClient() {
        // Cấu hình timeout
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.rest = new RestTemplate(factory);
    }

    /**
     * Gửi file đến một node cụ thể để node đó xử lý (lưu + tạo torrent + seed)
     */
    public void uploadFile(Contact node, MultipartFile file) throws IOException {
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

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

        System.out.println("✅ Upload response: " + response.getBody());
    }
}
