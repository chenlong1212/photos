package com.peachwuhu.app.recycle;

import com.peachwuhu.app.album.AlbumService;
import com.peachwuhu.app.storage.PhotoStorage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/recycle")
public class RecycleController {
    private final JdbcTemplate jdbc;
    private final AlbumService albums;
    private final PhotoStorage storage;

    public RecycleController(JdbcTemplate jdbc, AlbumService albums, PhotoStorage storage) {
        this.jdbc = jdbc;
        this.albums = albums;
        this.storage = storage;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return jdbc.queryForList("""
            SELECT id,origin_album_key AS originAlbum,origin_date AS originDate,filename,
                   raw_path AS rawPath,preview_path AS previewPath,photo_time AS photoTime,
                   deleted_at AS deletedAt
            FROM recycled_images ORDER BY deleted_at DESC
            """);
    }

    @PostMapping("/{id}/restore")
    @Transactional
    public Map<String, String> restore(@PathVariable long id, @RequestBody RestoreRequest request) throws IOException {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM recycled_images WHERE id=?", id);
        if (rows.isEmpty()) throw new NoSuchElementException("回收站图片不存在");
        Map<String, Object> recycled = rows.get(0);
        Map<String, Object> album = albums.requireAlbum(request.albumKey());
        long albumId = ((Number) album.get("id")).longValue();
        String folder = String.valueOf(album.get("folder_name"));
        int date = request.date();
        Path rawSource = storage.resolve(String.valueOf(recycled.get("raw_path")));
        Path previewSource = storage.resolve(String.valueOf(recycled.get("preview_path")));
        Path rawTarget = storage.unique(storage.resolve(folder + "/raw/" + date),
            String.valueOf(recycled.get("filename")));
        Path previewTarget = storage.unique(storage.resolve(folder + "/preview/" + date),
            stripExtension(rawTarget.getFileName().toString()) + ".jpg");
        if (!Files.exists(rawSource)) throw new NoSuchFileException("回收站原图不存在");
        storage.move(rawSource, rawTarget);
        if (Files.exists(previewSource)) storage.move(previewSource, previewTarget);
        else storage.createPreview(rawTarget, previewTarget);
        jdbc.update("""
            INSERT INTO album_days(album_id,photo_date,info) VALUES(?,?,?)
            ON DUPLICATE KEY UPDATE info=info
            """, albumId, date, date + " 的回忆");
        Integer order = jdbc.queryForObject(
            "SELECT COALESCE(MAX(sort_order),-1)+1 FROM images WHERE album_id=? AND photo_date=?",
            Integer.class, albumId, date);
        String photoTime = String.valueOf(recycled.getOrDefault("photo_time", ""));
        if (photoTime.isBlank() || "null".equals(photoTime)) photoTime = storage.extractPhotoTime(rawTarget);
        jdbc.update("""
            INSERT INTO images(
                album_id,photo_date,raw_path,preview_path,original_filename,sort_order,photo_time,file_size
            )
            VALUES(?,?,?,?,?,?,?,?)
            """, albumId, date, relative(rawTarget), relative(previewTarget), rawTarget.getFileName().toString(),
            order == null ? 0 : order, photoTime, Files.size(rawTarget));
        jdbc.update("DELETE FROM recycled_images WHERE id=?", id);
        return Map.of("status", "success");
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, String> delete(@PathVariable long id) throws IOException {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT raw_path,preview_path FROM recycled_images WHERE id=?", id);
        if (!rows.isEmpty()) {
            Files.deleteIfExists(storage.resolve(String.valueOf(rows.get(0).get("raw_path"))));
            Files.deleteIfExists(storage.resolve(String.valueOf(rows.get(0).get("preview_path"))));
            jdbc.update("DELETE FROM recycled_images WHERE id=?", id);
        }
        return Map.of("status", "success");
    }

    private String relative(Path path) {
        return storage.root().relativize(path).toString().replace('\\', '/');
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    public record RestoreRequest(String albumKey, int date) {}
}
