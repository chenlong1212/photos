package com.peachwuhu.app.album;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AlbumService {
    public static final Set<Integer> VALID_COVER_COUNTS = Set.of(1, 3, 4, 8, 9);
    private final JdbcTemplate jdbc;

    public AlbumService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> albums() {
        return jdbc.queryForList("""
            SELECT album_key AS `key`, label, folder_name AS folderName
            FROM albums ORDER BY sort_order, id
            """);
    }

    public Map<String, Object> requireAlbum(String key) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, album_key, label, folder_name FROM albums WHERE album_key = ?", key);
        if (rows.isEmpty()) throw new NoSuchElementException("相册不存在");
        return rows.get(0);
    }

    public Map<String, Object> timeline(String albumKey) {
        Map<String, Object> album = requireAlbum(albumKey);
        long albumId = ((Number) album.get("id")).longValue();
        List<Map<String, Object>> days = jdbc.queryForList("""
            SELECT d.photo_date AS date, d.info, COUNT(i.id) AS count,
                   COALESCE(
                       MIN(CASE
                           WHEN i.photo_time REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}'
                           THEN CAST(REPLACE(LEFT(i.photo_time, 10), '-', '') AS UNSIGNED)
                       END),
                       d.photo_date
                   ) AS startDate,
                   COALESCE(
                       MAX(CASE
                           WHEN i.photo_time REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}'
                           THEN CAST(REPLACE(LEFT(i.photo_time, 10), '-', '') AS UNSIGNED)
                       END),
                       d.photo_date
                   ) AS endDate
            FROM album_days d
            LEFT JOIN images i ON i.album_id=d.album_id AND i.photo_date=d.photo_date
            WHERE d.album_id=?
            GROUP BY d.id, d.photo_date, d.info
            HAVING count > 0
            ORDER BY startDate DESC, d.photo_date DESC
            """, albumId);
        for (Map<String, Object> day : days) {
            int date = ((Number) day.get("date")).intValue();
            List<Map<String, Object>> covers = jdbc.queryForList("""
                SELECT id, preview_path AS previewPath
                FROM images WHERE album_id=? AND photo_date=? AND is_cover=1
                ORDER BY sort_order,id
                """, albumId, date);
            if (!VALID_COVER_COUNTS.contains(covers.size())) {
                covers = jdbc.queryForList("""
                    SELECT id, preview_path AS previewPath FROM images
                    WHERE album_id=? AND photo_date=? ORDER BY sort_order,id LIMIT 1
                    """, albumId, date);
            }
            day.put("covers", covers);
            day.put("coverCount", covers.size());
            day.put("coverLayout", covers.size() == 3 || covers.size() == 4 ? 4 :
                covers.size() == 8 || covers.size() == 9 ? 9 : 1);
        }
        return Map.of("album", album, "days", days);
    }

    public Map<String, Object> day(String albumKey, int date) {
        Map<String, Object> album = requireAlbum(albumKey);
        long albumId = ((Number) album.get("id")).longValue();
        List<Map<String, Object>> days = jdbc.queryForList(
            "SELECT photo_date AS date, info FROM album_days WHERE album_id=? AND photo_date=?", albumId, date);
        if (days.isEmpty()) throw new NoSuchElementException("没有这一天的照片");
        List<Map<String, Object>> images = jdbc.queryForList("""
            SELECT id, photo_date AS date, raw_path AS rawPath, preview_path AS previewPath,
                   original_filename AS filename, sort_order AS sortOrder,
                   photo_time AS photoTime, is_cover AS isCover, file_size AS fileSize
            FROM images WHERE album_id=? AND photo_date=? ORDER BY sort_order,id
            """, albumId, date);
        return Map.of("album", album, "date", date, "info", days.get(0).get("info"), "images", images);
    }

    @Transactional
    public void updateDay(String albumKey, int date, String info, List<Long> order, List<Long> covers) {
        Map<String, Object> album = requireAlbum(albumKey);
        long albumId = ((Number) album.get("id")).longValue();
        jdbc.update("UPDATE album_days SET info=? WHERE album_id=? AND photo_date=?", info, albumId, date);
        for (int index = 0; index < order.size(); index++) {
            jdbc.update("UPDATE images SET sort_order=? WHERE id=? AND album_id=? AND photo_date=?",
                index, order.get(index), albumId, date);
        }
        setCovers(albumId, date, covers);
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM images WHERE album_id=? AND photo_date=?", Integer.class, albumId, date);
        if (count != null && count == 0 && (info == null || info.isBlank())) {
            jdbc.update("DELETE FROM album_days WHERE album_id=? AND photo_date=?", albumId, date);
        }
    }

    public void setCovers(long albumId, int date, List<Long> covers) {
        if (!covers.isEmpty() && !VALID_COVER_COUNTS.contains(covers.size())) {
            throw new IllegalArgumentException("封面图数量只能是 1、3、4、8 或 9 张");
        }
        jdbc.update("UPDATE images SET is_cover=0 WHERE album_id=? AND photo_date=?", albumId, date);
        for (Long id : new LinkedHashSet<>(covers)) {
            jdbc.update("UPDATE images SET is_cover=1 WHERE id=? AND album_id=? AND photo_date=?",
                id, albumId, date);
        }
    }

    @Transactional
    public void normalizeCovers(long albumId, int date) {
        List<Long> ids = jdbc.queryForList("""
            SELECT id FROM images WHERE album_id=? AND photo_date=? AND is_cover=1
            ORDER BY sort_order,id
            """, Long.class, albumId, date);
        if (ids.isEmpty() || VALID_COVER_COUNTS.contains(ids.size())) return;
        jdbc.update("UPDATE images SET is_cover=0 WHERE album_id=? AND photo_date=?", albumId, date);
        int keep = VALID_COVER_COUNTS.stream().filter(n -> n <= ids.size()).max(Integer::compareTo).orElse(0);
        for (int i = 0; i < keep; i++) jdbc.update("UPDATE images SET is_cover=1 WHERE id=?", ids.get(i));
    }
}
