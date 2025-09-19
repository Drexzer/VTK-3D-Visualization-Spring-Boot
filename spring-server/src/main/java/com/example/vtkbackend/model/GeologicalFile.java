package com.example.vtkbackend.model;

import java.time.LocalDateTime;
import java.util.Map;

public record GeologicalFile(
    String id,
    String originalFilename,
    String fileType,
    String mimeType,
    long sizeBytes,
    LocalDateTime uploadedAt,
    Map<String, Object> metadata,
    GeologicalData data
) {
    
    public enum FileType {
        CSV("csv"),
        SHAPEFILE("shp"),
        KML("kml"),
        KMZ("kmz"),
        DXF("dxf"),
        DWG("dwg"),
        TIFF("tif"),
        GEOTIFF("tiff"),
        STL("stl"),
        GEOJSON("geojson"),
        JSON("json"),
        GEOJSONL("geojsonl");
        
        private final String extension;
        
        FileType(String extension) {
            this.extension = extension;
        }
        
        public String getExtension() {
            return extension;
        }
        
        public static FileType fromExtension(String extension) {
            for (FileType type : values()) {
                if (type.extension.equalsIgnoreCase(extension)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unsupported file extension: " + extension);
        }
    }
}
