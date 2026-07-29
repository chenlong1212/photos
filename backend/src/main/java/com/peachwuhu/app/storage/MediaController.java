package com.peachwuhu.app.storage;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
public class MediaController {
    private final PhotoStorage storage;

    public MediaController(PhotoStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/media/{*path}")
    public ResponseEntity<Resource> media(@PathVariable String path) throws IOException {
        Resource resource = storage.resource(path.startsWith("/") ? path.substring(1) : path);
        MediaType type = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(type).body(resource);
    }
}
