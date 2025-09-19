package com.example.vtkbackend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StorageService {

    private final Path rootDir;

    public StorageService(@Value("${storage.root:uploads}") String rootDir) throws IOException {
        this.rootDir = Path.of(rootDir).toAbsolutePath();
        Files.createDirectories(this.rootDir);
    }

    public ModelInfo store(MultipartFile file) throws IOException {
        String id = UUID.randomUUID().toString();
        String filename = id + "_" + file.getOriginalFilename();
        Path dest = rootDir.resolve(filename);
        try (var in = file.getInputStream()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        return new ModelInfo(id, filename, Files.size(dest));
    }

    public List<ModelInfo> list() throws IOException {
        List<ModelInfo> items = new ArrayList<>();
        try (var stream = Files.list(rootDir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String name = p.getFileName().toString();
                int idx = name.indexOf('_');
                if (idx > 0) {
                    String id = name.substring(0, idx);
                    try {
                        items.add(new ModelInfo(id, name, Files.size(p)));
                    } catch (IOException ignored) {}
                }
            });
        }
        return items;
    }

    public Optional<Path> findById(String id) throws IOException {
        try (var stream = Files.list(rootDir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(id + "_"))
                    .findFirst();
        }
    }
}


