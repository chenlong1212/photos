package com.peachwuhu.app.admin;

import com.peachwuhu.app.album.AlbumService;
import com.peachwuhu.app.storage.PhotoStorage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api")
public class AdminController {
    private final JdbcTemplate jdbc;
    private final AlbumService albums;
    private final PhotoStorage storage;

    public AdminController(JdbcTemplate jdbc, AlbumService albums, PhotoStorage storage) {
        this.jdbc = jdbc;
        this.albums = albums;
        this.storage = storage;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "time", System.currentTimeMillis() / 1000);
    }

    @GetMapping("/admin/stats")
    public List<Map<String, Object>> stats() throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> album : albums.albums()) {
            String key = String.valueOf(album.get("key"));
            Map<String, Object> full = albums.requireAlbum(key);
            long id = ((Number) full.get("id")).longValue();
            String folder = String.valueOf(full.get("folder_name"));
            result.add(row(key,
                value("SELECT COUNT(*) FROM album_days WHERE album_id=?", id),
                value("SELECT COUNT(*) FROM images WHERE album_id=?", id),
                folder));
        }
        result.add(row("回收站 ♻️", "-", value("SELECT COUNT(*) FROM recycled_images"),
            "photos_recycle"));
        return result;
    }

    private Map<String, Object> row(String user, Object days, long images, String folder) throws IOException {
        DirStat raw = stat(storage.resolve(folder + "/raw"));
        DirStat preview = stat(storage.resolve(folder + "/preview"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user", user);
        row.put("dayCount", days);
        row.put("imageCount", images);
        row.put("rawCount", raw.count);
        row.put("rawSize", format(raw.bytes));
        row.put("rawAverage", format(raw.count == 0 ? 0 : raw.bytes / raw.count));
        row.put("previewCount", preview.count);
        row.put("previewSize", format(preview.bytes));
        return row;
    }

    private long value(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private DirStat stat(Path path) throws IOException {
        if (!Files.exists(path)) return new DirStat(0, 0);
        try (Stream<Path> stream = Files.walk(path)) {
            long[] stat = stream.filter(Files::isRegularFile).mapToLong(file -> {
                try { return Files.size(file); } catch (IOException ignored) { return 0; }
            }).collect(() -> new long[2], (a, size) -> { a[0]++; a[1] += size; },
                (a, b) -> { a[0] += b[0]; a[1] += b[1]; });
            return new DirStat(stat[0], stat[1]);
        }
    }

    private String format(long bytes) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double size = bytes;
        int unit = 0;
        while (size >= 1024 && unit < units.length - 1) { size /= 1024; unit++; }
        return String.format(Locale.ROOT, "%.2f %s", size, units[unit]);
    }

    private record DirStat(long count, long bytes) {}
}
