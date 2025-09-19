package com.example.vtkbackend.api;

import com.example.vtkbackend.model.GeologicalFile;
import com.example.vtkbackend.service.GeologicalFileService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/geological")
@CrossOrigin(origins = "*")
public class GeologicalFileController {

    private final GeologicalFileService geologicalFileService;

    public GeologicalFileController(GeologicalFileService geologicalFileService) {
        this.geologicalFileService = geologicalFileService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Geological file service is running");
    }


    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadGeologicalFile(@RequestPart("file") MultipartFile file) {
        try {
            GeologicalFile geologicalFile = geologicalFileService.processGeologicalFile(file);
            return ResponseEntity.status(HttpStatus.CREATED).body(geologicalFile);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Invalid file: " + e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to process file: " + e.getMessage()));
        }
    }

    @GetMapping("/files")
    public ResponseEntity<List<GeologicalFile>> listGeologicalFiles() {
        try {
            List<GeologicalFile> files = geologicalFileService.listGeologicalFiles();
            return ResponseEntity.ok(files);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/files/{id}")
    public ResponseEntity<byte[]> downloadGeologicalFile(@PathVariable String id) {
        try {
            Path filePath = geologicalFileService.findGeologicalFileById(id).orElse(null);
            if (filePath == null) {
                return ResponseEntity.notFound().build();
            }

            byte[] fileContent = Files.readAllBytes(filePath);
            String filename = filePath.getFileName().toString();
            
            // Remove the ID prefix from filename for download
            if (filename.contains("_")) {
                filename = filename.substring(filename.indexOf("_") + 1);
            }

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .body(fileContent);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/files/{id}/data")
    public ResponseEntity<?> getGeologicalFileData(@PathVariable String id) {
        try {
            Path filePath = geologicalFileService.findGeologicalFileById(id).orElse(null);
            if (filePath == null) {
                return ResponseEntity.notFound().build();
            }

            // Re-process the file to get the geological data
            String filename = filePath.getFileName().toString();
            String extension = filename.substring(filename.lastIndexOf(".") + 1);
            
            // Create a mock multipart file for processing
            byte[] fileContent = Files.readAllBytes(filePath);
            MockMultipartFile mockFile = new MockMultipartFile(filename, fileContent);
            
            GeologicalFile geologicalFile = geologicalFileService.processGeologicalFile(mockFile);
            return ResponseEntity.ok(geologicalFile.data());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to retrieve file data: " + e.getMessage()));
        }
    }

    @GetMapping("/supported-formats")
    public ResponseEntity<SupportedFormatsResponse> getSupportedFormats() {
        SupportedFormatsResponse response = new SupportedFormatsResponse(
            List.of("csv", "shp", "kml", "kmz", "dxf", "dwg", "tif", "tiff", "stl", "geojson", "json", "geojsonl"),
            "Upload geological files for 3D visualization"
        );
        return ResponseEntity.ok(response);
    }

    // Helper classes for responses
    public record ErrorResponse(String message) {}
    
    public record SupportedFormatsResponse(List<String> formats, String description) {}

    // Mock MultipartFile for internal processing
    private static class MockMultipartFile implements MultipartFile {
        private final String filename;
        private final byte[] content;

        public MockMultipartFile(String filename, byte[] content) {
            this.filename = filename;
            this.content = content;
        }

        @Override
        public String getName() { return "file"; }

        @Override
        public String getOriginalFilename() { return filename; }

        @Override
        public String getContentType() { return null; }

        @Override
        public boolean isEmpty() { return content.length == 0; }

        @Override
        public long getSize() { return content.length; }

        @Override
        public byte[] getBytes() { return content; }

        @Override
        public java.io.InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }
    }
}
