package com.peachwuhu.app.admin;

import com.peachwuhu.app.album.AlbumService;
import com.peachwuhu.app.storage.PhotoStorage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
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
    public Map<String, Object> stats() throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> album : albums.albums()) {
            String key = String.valueOf(album.get("key"));
            Map<String, Object> full = albums.requireAlbum(key);
            long id = ((Number) full.get("id")).longValue();
            rows.add(row(
                "album",
                key,
                String.valueOf(album.get("label")),
                value("SELECT COUNT(*) FROM album_days WHERE album_id=?", id),
                value("SELECT COUNT(*) FROM images WHERE album_id=?", id),
                String.valueOf(full.get("folder_name"))
            ));
        }

        Map<String, Object> recycle = row(
            "recycle",
            "recycle",
            "回收站",
            value("SELECT COUNT(DISTINCT origin_album_key, origin_date) FROM recycled_images"),
            value("SELECT COUNT(*) FROM recycled_images"),
            "photos_recycle"
        );

        DirStat totalRaw = statActive(storage.root(), "/raw/");
        DirStat totalPreview = statActive(storage.root(), "/preview/");
        Map<String, Object> total = summaryRow(
            "all",
            "all",
            "总图片",
            value("SELECT COUNT(*) FROM album_days"),
            value("SELECT COUNT(*) FROM images"),
            totalRaw,
            totalPreview
        );

        return Map.of("total", total, "albums", rows, "recycle", recycle);
    }

    @GetMapping("/admin/days")
    public Map<String, Object> days(
        @RequestParam(defaultValue = "all") String scope,
        @RequestParam(required = false) String albumKey
    ) {
        if ("recycle".equals(scope)) {
            return Map.of(
                "title", "回收站",
                "days", jdbc.queryForList("""
                    SELECT origin_date AS date, COUNT(*) AS imageCount, '' AS info
                    FROM recycled_images
                    GROUP BY origin_date
                    ORDER BY origin_date DESC
                    """)
            );
        }

        if ("album".equals(scope)) {
            Map<String, Object> album = albums.requireAlbum(albumKey);
            long albumId = ((Number) album.get("id")).longValue();
            return Map.of(
                "title", album.get("label"),
                "days", jdbc.queryForList("""
                    SELECT d.photo_date AS date, a.album_key AS albumKey,
                           COUNT(i.id) AS imageCount, d.info
                    FROM album_days d
                    JOIN albums a ON a.id=d.album_id
                    LEFT JOIN images i
                      ON i.album_id=d.album_id AND i.photo_date=d.photo_date
                    WHERE d.album_id=?
                    GROUP BY d.id, d.photo_date, a.album_key, d.info
                    ORDER BY d.photo_date DESC
                    """, albumId)
            );
        }

        return Map.of(
            "title", "总图片",
            "days", jdbc.queryForList("""
                SELECT d.photo_date AS date, a.album_key AS albumKey, a.label AS album,
                       COUNT(i.id) AS imageCount, d.info
                FROM album_days d
                JOIN albums a ON a.id=d.album_id
                LEFT JOIN images i
                  ON i.album_id=d.album_id AND i.photo_date=d.photo_date
                GROUP BY d.id, d.photo_date, a.album_key, a.label, d.info, a.sort_order
                ORDER BY d.photo_date DESC, a.sort_order, d.id
                """)
        );
    }

    @PatchMapping("/admin/albums/{albumKey}/days/{date}/description")
    public Map<String, String> updateDescription(
        @PathVariable String albumKey,
        @PathVariable int date,
        @RequestBody DescriptionRequest request
    ) {
        Map<String, Object> album = albums.requireAlbum(albumKey);
        long albumId = ((Number) album.get("id")).longValue();
        int updated = jdbc.update(
            "UPDATE album_days SET info=? WHERE album_id=? AND photo_date=?",
            request.info() == null ? "" : request.info(),
            albumId,
            date
        );
        if (updated == 0) throw new NoSuchElementException("没有这一天的记录");
        return Map.of("status", "success");
    }

    private Map<String, Object> row(
        String scope,
        String key,
        String label,
        long days,
        long images,
        String folder
    ) throws IOException {
        return summaryRow(
            scope,
            key,
            label,
            days,
            images,
            stat(storage.resolve(folder + "/raw")),
            stat(storage.resolve(folder + "/preview"))
        );
    }

    private Map<String, Object> summaryRow(
        String scope,
        String key,
        String label,
        long days,
        long images,
        DirStat raw,
        DirStat preview
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("scope", scope);
        row.put("key", key);
        row.put("label", label);
        row.put("dayCount", days);
        row.put("imageCount", images);
        row.put("rawCount", raw.count);
        row.put("rawSize", format(raw.bytes));
        row.put("rawAverage", format(raw.count == 0 ? 0 : raw.bytes / raw.count));
        row.put("previewCount", preview.count);
        row.put("previewSize", format(preview.bytes));
        row.put("previewAverage", format(preview.count == 0 ? 0 : preview.bytes / preview.count));
        return row;
    }

    private long value(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private DirStat stat(Path path) throws IOException {
        if (!Files.exists(path)) return new DirStat(0, 0);
        try (Stream<Path> stream = Files.walk(path)) {
            long[] result = stream.filter(Files::isRegularFile).mapToLong(file -> {
                try {
                    return Files.size(file);
                } catch (IOException ignored) {
                    return 0;
                }
            }).collect(
                () -> new long[2],
                (values, size) -> {
                    values[0]++;
                    values[1] += size;
                },
                (left, right) -> {
                    left[0] += right[0];
                    left[1] += right[1];
                }
            );
            return new DirStat(result[0], result[1]);
        }
    }

    private DirStat statActive(Path root, String pathPart) throws IOException {
        if (!Files.exists(root)) return new DirStat(0, 0);
        try (Stream<Path> stream = Files.walk(root)) {
            long[] result = stream
                .filter(Files::isRegularFile)
                .filter(file -> {
                    String normalized = file.toString().replace('\\', '/');
                    return normalized.contains(pathPart) && !normalized.contains("/photos_recycle/");
                })
                .mapToLong(file -> {
                    try {
                        return Files.size(file);
                    } catch (IOException ignored) {
                        return 0;
                    }
                })
                .collect(
                    () -> new long[2],
                    (values, size) -> {
                        values[0]++;
                        values[1] += size;
                    },
                    (left, right) -> {
                        left[0] += right[0];
                        left[1] += right[1];
                    }
                );
            return new DirStat(result[0], result[1]);
        }
    }

    private String format(long bytes) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double size = bytes;
        int unit = 0;
        while (size >= 1024 && unit < units.length - 1) {
            size /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%.2f %s", size, units[unit]);
    }

    private record DirStat(long count, long bytes) {}

    public record DescriptionRequest(String info) {}
}
