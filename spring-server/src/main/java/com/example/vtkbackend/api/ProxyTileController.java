package com.example.vtkbackend.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/tiles")
@CrossOrigin(origins = "*")
public class ProxyTileController {

    private static final Logger logger = LoggerFactory.getLogger(ProxyTileController.class);
    private final WebClient webClient = WebClient.builder().build();

    // OpenStreetMap raster tiles
    @GetMapping(value = "/osm/{z}/{x}/{y}.png", produces = MediaType.IMAGE_PNG_VALUE)
    @Cacheable(cacheNames = "tiles", key = "'osm-'+#z+'-'+#x+'-'+#y")
    public Mono<ResponseEntity<byte[]>> osm(@PathVariable int z, @PathVariable int x, @PathVariable int y) {
        String url = String.format("https://tile.openstreetmap.org/%d/%d/%d.png", z, x, y);
        logger.info("🗺️ Fetching OSM tile: z={}, x={}, y={} from {}", z, x, y, url);
        return fetchPng(url, "OSM");
    }

    // Terrarium elevation PNG (encoded height in RGB)
    @GetMapping(value = "/terrarium/{z}/{x}/{y}.png", produces = MediaType.IMAGE_PNG_VALUE)
    @Cacheable(cacheNames = "tiles", key = "'terrarium-'+#z+'-'+#x+'-'+#y")
    public Mono<ResponseEntity<byte[]>> terrarium(@PathVariable int z, @PathVariable int x, @PathVariable int y) {
        String url = String.format("https://s3.amazonaws.com/elevation-tiles-prod/terrarium/%d/%d/%d.png", z, x, y);
        logger.info("🏔️ Fetching Terrarium tile: z={}, x={}, y={} from {}", z, x, y, url);
        return fetchPng(url, "Terrarium");
    }

    private Mono<ResponseEntity<byte[]>> fetchPng(String url, String tileType) {
        return webClient.get().uri(url)
                .retrieve()
                .bodyToMono(byte[].class)
                .map(bytes -> {
                    logger.info("✅ Successfully fetched {} tile: {} bytes", tileType, bytes.length);
                    return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes);
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    logger.error("❌ Failed to fetch {} tile from {}: {} - {}", tileType, url, ex.getStatusCode(), ex.getMessage());
                    return Mono.just(ResponseEntity.status(ex.getStatusCode()).build());
                })
                .onErrorResume(Exception.class, ex -> {
                    logger.error("❌ Unexpected error fetching {} tile from {}: {}", tileType, url, ex.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}


