package app.controller;


import model.FileInfo;
import app.service.GatewayService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@Controller
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
        try {
            System.out.println("=== UPLOAD CALLED ===");
            System.out.println("File name: " + file.getOriginalFilename());
            System.out.println("File size: " + file.getSize());

            FileInfo info = gatewayService.store(file);

            System.out.println("Upload success: " + info);
            return ResponseEntity.ok(info);

        } catch (Exception e) {
            System.err.println("Upload error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }


    @GetMapping("/download")
    public String downloadPage() {
        return "download";
    }
    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password) {
        // logic login
        return "success";
    }

}