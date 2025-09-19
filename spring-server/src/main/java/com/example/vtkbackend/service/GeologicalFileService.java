package com.example.vtkbackend.service;

import com.example.vtkbackend.model.GeologicalData;
import com.example.vtkbackend.model.GeologicalFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class GeologicalFileService {

    @Value("${storage.root:uploads}")
    private String storageRoot;

    private final Tika tika = new Tika();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "csv", "shp", "kml", "kmz", "dxf", "dwg", "tif", "tiff", "stl", "geojson", "json", "geojsonl"
    );

    public GeologicalFile processGeologicalFile(MultipartFile file) throws IOException {
        validateFile(file);
        
        String id = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename).toLowerCase();
        String mimeType = tika.detect(file.getInputStream(), originalFilename);
        
        // Store the file
        Path storagePath = Paths.get(storageRoot).resolve(id + "_" + originalFilename);
        Files.createDirectories(storagePath.getParent());
        Files.copy(file.getInputStream(), storagePath, StandardCopyOption.REPLACE_EXISTING);
        
        // Process the file based on its type
        GeologicalData geologicalData = parseGeologicalFile(storagePath, extension);
        
        Map<String, Object> metadata = extractMetadata(storagePath, extension);
        
        return new GeologicalFile(
            id,
            originalFilename,
            extension,
            mimeType,
            file.getSize(),
            LocalDateTime.now(),
            metadata,
            geologicalData
        );
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename is required");
        }
        
        String extension = getFileExtension(filename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("File type not supported: " + extension);
        }
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1) : "";
    }

    private GeologicalData parseGeologicalFile(Path filePath, String extension) throws IOException {
        return switch (extension.toLowerCase()) {
            case "csv" -> parseCsvFile(filePath);
            case "geojson", "json" -> parseGeoJsonFile(filePath);
            case "geojsonl" -> parseGeoJsonLFile(filePath);
            case "kml" -> parseKmlFile(filePath);
            case "kmz" -> parseKmzFile(filePath);
            case "tif", "tiff" -> parseTiffFile(filePath);
            case "stl" -> parseStlFile(filePath);
            default -> createEmptyGeologicalData();
        };
    }

    private GeologicalData parseCsvFile(Path filePath) throws IOException {
        List<GeologicalData.Point3D> points = new ArrayList<>();
        Map<String, Object> properties = new HashMap<>();
        
        try (CSVReader reader = new CSVReader(new FileReader(filePath.toFile()))) {
            String[] headers = reader.readNext();
            if (headers == null) {
                throw new IOException("CSV file is empty");
            }
            
            // Find coordinate columns
            int xCol = findColumnIndex(headers, "x", "longitude", "lon", "easting");
            int yCol = findColumnIndex(headers, "y", "latitude", "lat", "northing");
            int zCol = findColumnIndex(headers, "z", "elevation", "height", "altitude");
            
            if (xCol == -1 || yCol == -1) {
                throw new IOException("CSV must contain X/Y coordinate columns");
            }
            
            String[] line;
            while ((line = reader.readNext()) != null) {
                try {
                    double x = Double.parseDouble(line[xCol]);
                    double y = Double.parseDouble(line[yCol]);
                    double z = zCol != -1 ? Double.parseDouble(line[zCol]) : 0.0;
                    points.add(new GeologicalData.Point3D(x, y, z));
                } catch (NumberFormatException e) {
                    // Skip invalid rows
                }
            }
            
            properties.put("totalPoints", points.size());
            properties.put("headers", Arrays.asList(headers));
        } catch (CsvValidationException e) {
            throw new IOException("Invalid CSV format: " + e.getMessage(), e);
        }
        
        return new GeologicalData(
            points,
            Collections.emptyList(),
            properties,
            calculateBoundingBox(points)
        );
    }

    private GeologicalData parseGeoJsonFile(Path filePath) throws IOException {
        JsonNode rootNode = objectMapper.readTree(filePath.toFile());
        List<GeologicalData.Point3D> points = new ArrayList<>();
        List<GeologicalData.Feature> features = new ArrayList<>();
        
        if (rootNode.has("features")) {
            for (JsonNode featureNode : rootNode.get("features")) {
                GeologicalData.Feature feature = parseGeoJsonFeature(featureNode);
                features.add(feature);
                
                // Extract points from geometry
                if (feature.geometry() != null) {
                    points.addAll(extractPointsFromGeometry(feature.geometry()));
                }
            }
        }
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("type", rootNode.get("type").asText());
        properties.put("featureCount", features.size());
        
        return new GeologicalData(
            points,
            features,
            properties,
            calculateBoundingBox(points)
        );
    }

    private GeologicalData parseGeoJsonLFile(Path filePath) throws IOException {
        List<GeologicalData.Point3D> points = new ArrayList<>();
        List<GeologicalData.Feature> features = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    JsonNode featureNode = objectMapper.readTree(line);
                    GeologicalData.Feature feature = parseGeoJsonFeature(featureNode);
                    features.add(feature);
                    
                    if (feature.geometry() != null) {
                        points.addAll(extractPointsFromGeometry(feature.geometry()));
                    }
                }
            }
        }
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("type", "FeatureCollection");
        properties.put("featureCount", features.size());
        
        return new GeologicalData(
            points,
            features,
            properties,
            calculateBoundingBox(points)
        );
    }

    private GeologicalData parseKmlFile(Path filePath) throws IOException {
        // Basic KML parsing - extract coordinates from placemark elements
        List<GeologicalData.Point3D> points = new ArrayList<>();
        String content = Files.readString(filePath);
        
        // Simple regex-based extraction for coordinates
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "<coordinates>([^<]+)</coordinates>", 
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            String coordinates = matcher.group(1).trim();
            String[] coordPairs = coordinates.split("\\s+");
            
            for (String coordPair : coordPairs) {
                String[] coords = coordPair.split(",");
                if (coords.length >= 2) {
                    try {
                        double x = Double.parseDouble(coords[0]);
                        double y = Double.parseDouble(coords[1]);
                        double z = coords.length > 2 ? Double.parseDouble(coords[2]) : 0.0;
                        points.add(new GeologicalData.Point3D(x, y, z));
                    } catch (NumberFormatException e) {
                        // Skip invalid coordinates
                    }
                }
            }
        }
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("type", "KML");
        properties.put("totalPoints", points.size());
        
        return new GeologicalData(
            points,
            Collections.emptyList(),
            properties,
            calculateBoundingBox(points)
        );
    }

    private GeologicalData parseKmzFile(Path filePath) throws IOException {
        // Extract KML from KMZ (ZIP) file
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(filePath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().toLowerCase().endsWith(".kml")) {
                    // Create temporary KML file
                    Path tempKml = Files.createTempFile("temp", ".kml");
                    Files.copy(zis, tempKml, StandardCopyOption.REPLACE_EXISTING);
                    
                    GeologicalData result = parseKmlFile(tempKml);
                    Files.delete(tempKml);
                    return result;
                }
            }
        }
        
        throw new IOException("No KML file found in KMZ archive");
    }

    private GeologicalData parseTiffFile(Path filePath) throws IOException {
        // Basic TIFF parsing - create grid points for elevation data
        // This is a simplified implementation
        List<GeologicalData.Point3D> points = new ArrayList<>();
        
        // For demonstration, create a synthetic elevation grid
        // In a real implementation, you would use a TIFF library like JAI or ImageIO
        int gridSize = 50;
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                double x = i * 10.0;
                double y = j * 10.0;
                double z = Math.sin(i * 0.1) * Math.cos(j * 0.1) * 100; // Synthetic elevation
                points.add(new GeologicalData.Point3D(x, y, z));
            }
        }
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("type", "TIFF");
        properties.put("gridSize", gridSize);
        properties.put("totalPoints", points.size());
        
        return new GeologicalData(
            points,
            Collections.emptyList(),
            properties,
            calculateBoundingBox(points)
        );
    }

    private GeologicalData parseStlFile(Path filePath) throws IOException {
        // Basic STL parsing - extract vertices from triangular meshes
        List<GeologicalData.Point3D> points = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("vertex")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 4) {
                        try {
                            double x = Double.parseDouble(parts[1]);
                            double y = Double.parseDouble(parts[2]);
                            double z = Double.parseDouble(parts[3]);
                            points.add(new GeologicalData.Point3D(x, y, z));
                        } catch (NumberFormatException e) {
                            // Skip invalid vertices
                        }
                    }
                }
            }
        }
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("type", "STL");
        properties.put("totalVertices", points.size());
        
        return new GeologicalData(
            points,
            Collections.emptyList(),
            properties,
            calculateBoundingBox(points)
        );
    }

    private GeologicalData createEmptyGeologicalData() {
        return new GeologicalData(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyMap(),
            new GeologicalData.BoundingBox(0, 0, 0, 0, 0, 0)
        );
    }

    private int findColumnIndex(String[] headers, String... possibleNames) {
        for (int i = 0; i < headers.length; i++) {
            for (String name : possibleNames) {
                if (headers[i].toLowerCase().contains(name.toLowerCase())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private GeologicalData.Feature parseGeoJsonFeature(JsonNode featureNode) {
        String id = featureNode.has("id") ? featureNode.get("id").asText() : UUID.randomUUID().toString();
        String type = featureNode.has("type") ? featureNode.get("type").asText() : "Feature";
        
        GeologicalData.Geometry geometry = null;
        if (featureNode.has("geometry")) {
            JsonNode geometryNode = featureNode.get("geometry");
            String geometryType = geometryNode.get("type").asText();
            List<List<Double>> coordinates = new ArrayList<>();
            
            if (geometryNode.has("coordinates")) {
                // Parse coordinates based on geometry type
                coordinates = parseCoordinates(geometryNode.get("coordinates"));
            }
            
            geometry = new GeologicalData.Geometry(geometryType, coordinates);
        }
        
        Map<String, Object> properties = new HashMap<>();
        if (featureNode.has("properties")) {
            JsonNode propertiesNode = featureNode.get("properties");
            propertiesNode.fields().forEachRemaining(entry -> {
                properties.put(entry.getKey(), entry.getValue().asText());
            });
        }
        
        return new GeologicalData.Feature(id, type, geometry, properties);
    }

    private List<List<Double>> parseCoordinates(JsonNode coordinatesNode) {
        List<List<Double>> coordinates = new ArrayList<>();
        
        if (coordinatesNode.isArray()) {
            for (JsonNode coordNode : coordinatesNode) {
                if (coordNode.isArray()) {
                    List<Double> coord = new ArrayList<>();
                    for (JsonNode valueNode : coordNode) {
                        if (valueNode.isNumber()) {
                            coord.add(valueNode.asDouble());
                        }
                    }
                    if (!coord.isEmpty()) {
                        coordinates.add(coord);
                    }
                }
            }
        }
        
        return coordinates;
    }

    private List<GeologicalData.Point3D> extractPointsFromGeometry(GeologicalData.Geometry geometry) {
        List<GeologicalData.Point3D> points = new ArrayList<>();
        
        for (List<Double> coord : geometry.coordinates()) {
            if (coord.size() >= 2) {
                double x = coord.get(0);
                double y = coord.get(1);
                double z = coord.size() > 2 ? coord.get(2) : 0.0;
                points.add(new GeologicalData.Point3D(x, y, z));
            }
        }
        
        return points;
    }

    private GeologicalData.BoundingBox calculateBoundingBox(List<GeologicalData.Point3D> points) {
        if (points.isEmpty()) {
            return new GeologicalData.BoundingBox(0, 0, 0, 0, 0, 0);
        }
        
        double minX = points.get(0).x();
        double minY = points.get(0).y();
        double minZ = points.get(0).z();
        double maxX = minX;
        double maxY = minY;
        double maxZ = minZ;
        
        for (GeologicalData.Point3D point : points) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
            maxZ = Math.max(maxZ, point.z());
        }
        
        return new GeologicalData.BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private Map<String, Object> extractMetadata(Path filePath, String extension) throws IOException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileSize", Files.size(filePath));
        metadata.put("fileType", extension);
        metadata.put("processedAt", LocalDateTime.now().toString());
        
        return metadata;
    }

    public List<GeologicalFile> listGeologicalFiles() throws IOException {
        Path storageDir = Paths.get(storageRoot);
        if (!Files.exists(storageDir)) {
            return Collections.emptyList();
        }
        
        List<GeologicalFile> files = new ArrayList<>();
        // This is a simplified implementation - in production you'd store metadata in a database
        return files;
    }

    public Optional<Path> findGeologicalFileById(String id) throws IOException {
        Path storageDir = Paths.get(storageRoot);
        if (!Files.exists(storageDir)) {
            return Optional.empty();
        }
        
        try (var stream = Files.list(storageDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().startsWith(id + "_"))
                .findFirst();
        }
    }
}
