package com.example.vtkbackend.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

@RestController
@RequestMapping("/api/tiles")
@CrossOrigin(origins = "*")
public class TileController {

    @GetMapping(value = "/{layer}/{z}/{x}/{y}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> xyz(@PathVariable String layer,
                                      @PathVariable int z,
                                      @PathVariable int x,
                                      @PathVariable int y) throws Exception {
        // Placeholder: procedural tile image with grid + coordinates
        int size = 256;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, size, size);
        g.setColor(new Color(230,230,230));
        for (int i = 0; i <= size; i += 32) {
            g.drawLine(i, 0, i, size);
            g.drawLine(0, i, size, i);
        }
        g.setColor(new Color(40,40,40));
        g.drawString(layer + " z=" + z + " x=" + x + " y=" + y, 10, 20);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(baos.toByteArray());
    }
}


   