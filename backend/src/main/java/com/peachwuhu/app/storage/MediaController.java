package com.peachwuhu.app.storage;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
public class MediaController {
    private final PhotoStorage storage;
    private final JdbcTemplate jdbc;

    public MediaController(PhotoStorage storage, JdbcTemplate jdbc) {
        this.storage = storage;
        this.jdbc = jdbc;
    }

    @GetMapping("/media/{*path}")
    public ResponseEntity<Resource> media(@PathVariable String path) throws IOException {
        String relativePath = path.startsWith("/") ? path.substring(1) : path;
        Resource resource = storage.resource(relativePath);
        MediaType type = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        var metadata = relativePath.contains("/raw/")
            ? jdbc.queryForList("SELECT photo_time AS photoTime,media_type AS mediaType FROM images WHERE raw_path=? LIMIT 1", relativePath)
            : java.util.List.<java.util.Map<String, Object>>of();
        if (!metadata.isEmpty()
            && "photo".equals(metadata.get(0).get("mediaType"))
            && metadata.get(0).get("photoTime") != null
            && !String.valueOf(metadata.get(0).get("photoTime")).isBlank()) {
            byte[] content = storage.withPhotoTimeExif(
                storage.resolve(relativePath), String.valueOf(metadata.get(0).get("photoTime")));
            resource = new ByteArrayResource(content) {
                @Override public String getFilename() { return storage.resolve(relativePath).getFileName().toString(); }
            };
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(type).body(resource);
    }
}
