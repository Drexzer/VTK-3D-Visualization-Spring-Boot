# Geological File Upload & 3D Visualization System
## Simple Project Flow & API Communication

## 🎯 **Project Overview**
A system where users upload geological files through a React frontend, which sends them to a Spring Boot backend for processing, then displays the data as 3D visualizations.

---

## 🏗️ **System Components**

### **Frontend: React.js (Port 3000)**
- User interface with upload button
- 3D visualization display
- File selection and progress

### **Backend: Spring Boot (Port 8085)**
- File processing and parsing
- Data storage and retrieval
- REST API endpoints

---

## 🔄 **Complete Project Flow**

### **Step 1: User Selects File**
```
User clicks "Choose File" → Selects geological file (CSV, GeoJSON, etc.)
```

### **Step 2: Upload Button Click**
```
User clicks "Upload" → Frontend prepares API call
```

### **Step 3: Frontend API Call**
```javascript
// FileUpload.js
const handleUpload = async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    
    const response = await axios.post('http://localhost:8085/api/geological/upload', formData);
    // Response contains file ID and metadata
};
```

### **Step 4: Backend Processing**
```java
// GeologicalFileController.java
@PostMapping("/upload")
public ResponseEntity<GeologicalFile> uploadFile(@RequestParam("file") MultipartFile file) {
    // 1. Save file to uploads/ directory
    // 2. Parse coordinates from file
    // 3. Extract metadata (bounds, count)
    // 4. Return file info with unique ID
}
```

### **Step 5: Get Processed Data**
```javascript
// Frontend requests the processed coordinate data
const response = await axios.get(`http://localhost:8085/api/geological/files/${fileId}/data`);
// Response contains all coordinate points for 3D rendering
```

### **Step 6: 3D Visualization**
```javascript
// VtkViewer.js
const renderGeologicalData = (data) => {
    // Create 3D spheres for each coordinate point
    // Generate terrain surface from elevation data
    // Display interactive 3D scene
};
```

---

## 📡 **API Communication Details**

### **Upload API Call**
```
Method: POST
URL: http://localhost:8085/api/geological/upload
Content-Type: multipart/form-data
Body: file = [selected_file.csv]

Response:
{
    "id": "uuid-123",
    "filename": "survey_data.csv",
    "format": "CSV",
    "uploadTime": "2024-01-15T10:30:00"
}
```

### **Data Retrieval API Call**
```
Method: GET
URL: http://localhost:8085/api/geological/files/uuid-123/data

Response:
{
    "coordinateCount": 1250,
    "bounds": {"minX": 500000, "maxX": 501200, "minZ": 125, "maxZ": 170},
    "coordinates": [
        {"x": 500000, "y": 4500000, "z": 125},
        {"x": 500050, "y": 4500025, "z": 130}
    ]
}
```

---

## 🔧 **How to Run**

### **Start Backend**
```bash
cd spring-server
mvn spring-boot:run
# Runs on http://localhost:8085
```

### **Start Frontend**
```bash
cd vtk-react-app
npm start
# Runs on http://localhost:3000
```

---

## 📋 **Simple Summary for Team Lead**

1. **User uploads file** through React interface
2. **Frontend sends file** to Spring Boot via POST API
3. **Backend processes file** and extracts coordinate data
4. **Frontend requests processed data** via GET API
5. **3D visualization renders** the coordinate points as interactive spheres and terrain

**Key Point**: Frontend and backend communicate through simple REST API calls - upload file, get processed data, display in 3D.
