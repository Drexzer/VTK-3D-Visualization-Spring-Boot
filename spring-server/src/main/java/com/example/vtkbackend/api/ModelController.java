package com.example.vtkbackend.api;

import com.example.vtkbackend.storage.ModelInfo;
import com.example.vtkbackend.storage.StorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ModelController {

    private final StorageService storageService;

    public ModelController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok().body("ok");
    }

    @PostMapping(value = "/models", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ModelInfo> upload(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || !StringUtils.hasText(file.getOriginalFilename())) {
            return ResponseEntity.badRequest().build();
        }
        ModelInfo info = storageService.store(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(info);
    }

    @GetMapping("/models")
    public List<ModelInfo> list() throws IOException {
        return storageService.list();
    }

    @GetMapping("/models/{id}")
    public ResponseEntity<byte[]> download(@PathVariable String id) throws IOException {
        Path path = storageService.findById(id).orElse(null);
        if (path == null) return ResponseEntity.notFound().build();
        byte[] bytes = java.nio.file.Files.readAllBytes(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + path.getFileName())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }
}


