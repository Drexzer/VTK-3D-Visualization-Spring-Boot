package com.example.vtkbackend.api;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/tracks")
@CrossOrigin(origins = "*")
public class TrackSseController {

    @GetMapping(path = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream() {
        AtomicLong counter = new AtomicLong();
        // Dummy circular track around (lat,lon) with altitude changes
        return Flux.interval(Duration.ofMillis(250)).map(i -> {
            long t = counter.incrementAndGet();
            double angle = (t % 360) * Math.PI / 180.0;
            double lat = 28.6139 + 0.05 * Math.sin(angle);
            double lon = 77.2090 + 0.05 * Math.cos(angle);
            double alt = 1000 + 100 * Math.sin(angle * 3);
            String json = String.format("{\"t\":%d,\"lat\":%.6f,\"lon\":%.6f,\"alt\":%.2f}", t, lat, lon, alt);
            return ServerSentEvent.<String>builder().event("track").data(json).build();
        });
    }
}


