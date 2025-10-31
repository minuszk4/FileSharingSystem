package app.controller;


import model.FileInfo;
import app.service.GatewayService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/")
public class FileController {


    private final GatewayService gatewayService;


    public FileController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }


    @GetMapping
    public String index() {
        return "index";
    }


    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<FileInfo> handleUpload(@RequestParam("file") MultipartFile file) {
        System.out.println("file upload has called");
        try {
            System.out.println("=== UPLOAD CALLED ===");
            System.out.println("File name: " + file.getOriginalFilename());
            System.out.println("File size: " + file.getSize());

            FileInfo info = gatewayService.store(file);

            System.out.println("Upload success: " + info.getHash());
            return ResponseEntity.ok(info);

        } catch (Exception e) {
            System.err.println("Upload error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }
    @GetMapping("/find")
    @ResponseBody
    public ResponseEntity<?> findFile(@RequestParam String hash) {
        System.out.println("hash: " + hash);
        try {
            System.out.println("=== FIND HASH ===");
            FileInfo info = gatewayService.findFileByHash(hash);
            if (info == null) {
                System.out.println("File not found for hash: " + hash);
                Map<String, Object> response = new HashMap<>();
                response.put("error", "File not found");
                return ResponseEntity.status(404).body(response);
            }
            System.out.println("File found: " + info.getName());

            // Trả thông tin JSON để frontend hiển thị
            Map<String, Object> response = new HashMap<>();
            response.put("fileName", info.getName());
            response.put("size", info.getSize());
            response.put("infoHash", info.getHash());
            return ResponseEntity.ok(response);
        } catch (Exception e){
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }

    }


    @PostMapping("/login")
    @ResponseBody
    public String login(@RequestParam String username, @RequestParam String password) {
        // logic login
        return "success";
    }

}