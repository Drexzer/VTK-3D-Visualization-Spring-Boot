package com.example.vtkbackend.model;

import java.util.List;
import java.util.Map;

public record GeologicalData(
    List<Point3D> points,
    List<Feature> features,
    Map<String, Object> properties,
    BoundingBox boundingBox
) {
    
    public record Point3D(double x, double y, double z) {}
    
    public record Feature(
        String id,
        String type,
        Geometry geometry,
        Map<String, Object> properties
    ) {}
    
    public record Geometry(
        String type,
        List<List<Double>> coordinates
    ) {}
    
    public record BoundingBox(
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ
    ) {}
}
