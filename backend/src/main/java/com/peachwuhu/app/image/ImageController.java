package com.peachwuhu.app.image;

import com.peachwuhu.app.album.AlbumService;
import com.peachwuhu.app.storage.PhotoStorage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ImageController {
    private final JdbcTemplate jdbc;
    private final AlbumService albums;
    private final PhotoStorage storage;

    public ImageController(JdbcTemplate jdbc, AlbumService albums, PhotoStorage storage) {
        this.jdbc = jdbc;
        this.albums = albums;
        this.storage = storage;
    }

    @PostMapping("/albums/{albumKey}/images")
    @Transactional
    public Map<String, Object> upload(
        @PathVariable String albumKey,
        @RequestParam int date,
        @RequestParam(required = false, defaultValue = "") String info,
        @RequestParam(required = false, defaultValue = "false") boolean updateInfo,
        @RequestParam(required = false) List<String> photoUids,
        @RequestParam(required = false) List<String> photoTimes,
        @RequestParam(required = false) List<String> coverUids,
        @RequestPart("photos") List<MultipartFile> photos
    ) throws IOException {
        if (String.valueOf(date).length() != 8) throw new IllegalArgumentException("日期格式不正确");
        Map<String, Object> album = albums.requireAlbum(albumKey);
        long albumId = ((Number) album.get("id")).longValue();
        String folder = String.valueOf(album.get("folder_name"));
        Integer existing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM images WHERE album_id=? AND photo_date=?", Integer.class, albumId, date);
        boolean newPost = existing == null || existing == 0;

        jdbc.update("""
            INSERT INTO album_days(album_id, photo_date, info) VALUES(?,?,?)
            ON DUPLICATE KEY UPDATE info=IF(?, VALUES(info), info)
            """, albumId, date, info.isBlank() ? date + " 的回忆" : info, updateInfo);

        Set<String> selected = new LinkedHashSet<>(coverUids == null ? List.of() : coverUids);
        if (!selected.isEmpty() && !AlbumService.VALID_COVER_COUNTS.contains(selected.size())) {
            throw new IllegalArgumentException("封面图数量只能是 1、3、4、8 或 9 张");
        }
        List<Long> selectedIds = new ArrayList<>();
        List<Map<String, String>> invalid = new ArrayList<>();
        long bytes = 0;
        int success = 0;

        for (int index = 0; index < photos.size(); index++) {
            MultipartFile photo = photos.get(index);
            String photoTime = photoTimes != null && index < photoTimes.size() ? photoTimes.get(index) : "";
            String original = storage.sanitizeFilename(photo.getOriginalFilename());
            if (photo.isEmpty() || !storage.allowed(original)) {
                invalid.add(Map.of("name", original, "reason", "文件格式不支持"));
                continue;
            }
            Path rawDirectory = storage.resolve(folder + "/raw/" + date);
            Path raw = storage.unique(rawDirectory, original);
            String previewFilename = stripExtension(raw.getFileName().toString()) + ".jpg";
            Path preview = storage.unique(storage.resolve(folder + "/preview/" + date), previewFilename);
            try {
                photo.transferTo(raw);
                storage.createPreview(raw, preview);
                if (photoTime.isBlank()) photoTime = storage.extractPhotoTime(raw);
                Integer maxOrder = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(sort_order),-1) FROM images WHERE album_id=? AND photo_date=?",
                    Integer.class, albumId, date);
                String rawPath = storage.root().relativize(raw).toString().replace('\\', '/');
                String previewPath = storage.root().relativize(preview).toString().replace('\\', '/');
                jdbc.update("""
                    INSERT INTO images(album_id,photo_date,raw_path,preview_path,original_filename,sort_order,photo_time,file_size)
                    VALUES(?,?,?,?,?,?,?,?)
                    """, albumId, date, rawPath, previewPath, raw.getFileName().toString(),
                    (maxOrder == null ? -1 : maxOrder) + 1, photoTime, Files.size(raw));
                Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                String uid = photoUids != null && index < photoUids.size() ? photoUids.get(index) : "";
                if (selected.contains(uid) && id != null) selectedIds.add(id);
                bytes += Files.size(raw);
                success++;
            } catch (Exception exception) {
                Files.deleteIfExists(raw);
                Files.deleteIfExists(preview);
                invalid.add(Map.of("name", original, "reason", exception.getMessage()));
            }
        }
        if (success == 0) throw new IllegalArgumentException("没有成功保存任何图片");
        if (newPost) {
            List<Long> ids = jdbc.queryForList("""
                SELECT id FROM images WHERE album_id=? AND photo_date=?
                ORDER BY CASE WHEN photo_time='' THEN 1 ELSE 0 END,photo_time,id
                """, Long.class, albumId, date);
            for (int i = 0; i < ids.size(); i++) jdbc.update("UPDATE images SET sort_order=? WHERE id=?", i, ids.get(i));
        }
        if (coverUids != null) albums.setCovers(albumId, date, selectedIds);
        return Map.of("status", "success", "count", success, "skipped", invalid.size(),
            "invalidFiles", invalid, "date", date, "totalSizeBytes", bytes);
    }

    @DeleteMapping("/albums/{albumKey}/images/{imageId}")
    @Transactional
    public Map<String, Object> delete(@PathVariable String albumKey, @PathVariable long imageId) throws IOException {
        Map<String, Object> album = albums.requireAlbum(albumKey);
        long albumId = ((Number) album.get("id")).longValue();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id,photo_date,raw_path,preview_path,original_filename,photo_time
            FROM images WHERE id=? AND album_id=?
            """, imageId, albumId);
        if (rows.isEmpty()) throw new NoSuchElementException("图片不存在");
        Map<String, Object> image = rows.get(0);
        String stamp = String.valueOf(System.currentTimeMillis() / 1000);
        Path rawSource = storage.resolve(String.valueOf(image.get("raw_path")));
        Path previewSource = storage.resolve(String.valueOf(image.get("preview_path")));
        Path rawTarget = storage.unique(storage.resolve("photos_recycle/raw"),
            stamp + "_" + rawSource.getFileName());
        Path previewTarget = storage.unique(storage.resolve("photos_recycle/preview"),
            stamp + "_" + previewSource.getFileName());
        if (Files.exists(rawSource)) storage.move(rawSource, rawTarget);
        if (Files.exists(previewSource)) storage.move(previewSource, previewTarget);
        jdbc.update("""
            INSERT INTO recycled_images(
                origin_album_key,origin_date,filename,raw_path,preview_path,photo_time,deleted_at
            )
            VALUES(?,?,?,?,?,?,?)
            """, albumKey, image.get("photo_date"), image.get("original_filename"),
            relative(rawTarget), relative(previewTarget), image.get("photo_time"), LocalDateTime.now());
        jdbc.update("DELETE FROM images WHERE id=?", imageId);
        int date = ((Number) image.get("photo_date")).intValue();
        albums.normalizeCovers(albumId, date);
        Integer remaining = jdbc.queryForObject(
            "SELECT COUNT(*) FROM images WHERE album_id=? AND photo_date=?", Integer.class, albumId, date);
        if (remaining != null && remaining == 0) {
            jdbc.update("DELETE FROM album_days WHERE album_id=? AND photo_date=?", albumId, date);
        }
        return Map.of("status", "success", "remaining", remaining == null ? 0 : remaining);
    }

    private String relative(Path path) {
        return storage.root().relativize(path).toString().replace('\\', '/');
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
