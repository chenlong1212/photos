package com.peachwuhu.app.admin;

import com.peachwuhu.app.album.AlbumService;
import com.peachwuhu.app.storage.PhotoStorage;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminTransferController {
    private final JdbcTemplate jdbc;
    private final AlbumService albums;
    private final PhotoStorage storage;

    public AdminTransferController(JdbcTemplate jdbc, AlbumService albums, PhotoStorage storage) {
        this.jdbc = jdbc;
        this.albums = albums;
        this.storage = storage;
    }

    @GetMapping("/transfer/preview")
    public Map<String, Object> preview(
        @RequestParam String albumKey,
        @RequestParam int date
    ) {
        validateDate(date);
        Map<String, Object> album = albums.requireAlbum(albumKey);
        long albumId = ((Number) album.get("id")).longValue();
        List<Map<String, Object>> days = jdbc.queryForList(
            "SELECT info FROM album_days WHERE album_id=? AND photo_date=?",
            albumId,
            date
        );
        List<Map<String, Object>> images = jdbc.queryForList("""
            SELECT id,preview_path AS previewPath,photo_time AS photoTime,
                   media_type AS mediaType,duration_ms AS durationMs
            FROM images
            WHERE album_id=? AND photo_date=?
            ORDER BY sort_order,id
            """, albumId, date);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", !images.isEmpty());
        result.put("info", days.isEmpty() || days.get(0).get("info") == null ? "" : days.get(0).get("info"));
        result.put("images", images);
        return result;
    }

    @PostMapping("/transfer")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> transfer(@RequestBody TransferRequest request) throws IOException {
        validateDate(request.sourceDate());
        validateDate(request.targetDate());
        if (request.sourceAlbumKey() == null || request.targetAlbumKey() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择原相册和目标相册");
        }
        if (request.sourceAlbumKey().equals(request.targetAlbumKey())
            && request.sourceDate() == request.targetDate()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原位置和目标位置不能相同");
        }

        Map<String, Object> sourceAlbum = albums.requireAlbum(request.sourceAlbumKey());
        Map<String, Object> targetAlbum = albums.requireAlbum(request.targetAlbumKey());
        long sourceAlbumId = ((Number) sourceAlbum.get("id")).longValue();
        long targetAlbumId = ((Number) targetAlbum.get("id")).longValue();

        List<Map<String, Object>> images = jdbc.queryForList("""
            SELECT id,raw_path,preview_path,sort_order,is_cover
            FROM images
            WHERE album_id=? AND photo_date=?
            ORDER BY sort_order,id
            """, sourceAlbumId, request.sourceDate());
        if (images.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原日期没有可转移的图片");
        }

        Integer targetCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM images WHERE album_id=? AND photo_date=?",
            Integer.class, targetAlbumId, request.targetDate());
        boolean targetEmpty = targetCount == null || targetCount == 0;
        String description = request.description() == null ? "" : request.description().trim();
        if (targetEmpty && description.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标日期为空，请填写图片描述");
        }

        List<FileMove> moves = planMoves(
            images,
            String.valueOf(targetAlbum.get("folder_name")),
            request.targetDate()
        );
        List<FileMove> completed = new ArrayList<>();
        try {
            for (FileMove move : moves) {
                storage.move(move.source(), move.target());
                completed.add(move);
            }

            if (targetEmpty) {
                jdbc.update("""
                    INSERT INTO album_days(album_id,photo_date,info) VALUES(?,?,?)
                    ON DUPLICATE KEY UPDATE info=VALUES(info)
                    """, targetAlbumId, request.targetDate(), description);
            }

            Integer maxOrder = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sort_order),-1) FROM images WHERE album_id=? AND photo_date=?",
                Integer.class, targetAlbumId, request.targetDate());
            int nextOrder = (maxOrder == null ? -1 : maxOrder) + 1;
            for (int index = 0; index < images.size(); index++) {
                Map<String, Object> image = images.get(index);
                FileMove rawMove = moves.get(index * 2);
                FileMove previewMove = moves.get(index * 2 + 1);
                Object coverValue = image.get("is_cover");
                boolean sourceCover = coverValue instanceof Boolean booleanValue
                    ? booleanValue
                    : coverValue instanceof Number numberValue && numberValue.intValue() != 0;
                boolean keepCover = targetEmpty && sourceCover;
                jdbc.update("""
                    UPDATE images
                    SET album_id=?,photo_date=?,raw_path=?,preview_path=?,sort_order=?,is_cover=?
                    WHERE id=? AND album_id=? AND photo_date=?
                    """,
                    targetAlbumId,
                    request.targetDate(),
                    relative(rawMove.target()),
                    relative(previewMove.target()),
                    nextOrder + index,
                    keepCover,
                    image.get("id"),
                    sourceAlbumId,
                    request.sourceDate()
                );
            }
            jdbc.update(
                "DELETE FROM album_days WHERE album_id=? AND photo_date=?",
                sourceAlbumId,
                request.sourceDate()
            );
            if (!targetEmpty) albums.normalizeCovers(targetAlbumId, request.targetDate());
            return Map.of(
                "status", "success",
                "count", images.size(),
                "sourceDate", request.sourceDate(),
                "targetDate", request.targetDate()
            );
        } catch (Exception exception) {
            rollbackMoves(completed);
            throw exception;
        }
    }

    private List<FileMove> planMoves(
        List<Map<String, Object>> images,
        String targetFolder,
        int targetDate
    ) throws IOException {
        List<FileMove> moves = new ArrayList<>();
        for (Map<String, Object> image : images) {
            Path rawSource = storage.resolve(String.valueOf(image.get("raw_path")));
            Path previewSource = storage.resolve(String.valueOf(image.get("preview_path")));
            if (!Files.isRegularFile(rawSource)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "原图文件不存在：" + rawSource.getFileName());
            }
            if (!Files.isRegularFile(previewSource)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "缩略图文件不存在：" + previewSource.getFileName());
            }
            Path rawTarget = storage.unique(
                storage.resolve(targetFolder + "/raw/" + targetDate),
                rawSource.getFileName().toString()
            );
            Path previewTarget = storage.unique(
                storage.resolve(targetFolder + "/preview/" + targetDate),
                previewSource.getFileName().toString()
            );
            moves.add(new FileMove(rawSource, rawTarget));
            moves.add(new FileMove(previewSource, previewTarget));
        }
        return moves;
    }

    private void rollbackMoves(List<FileMove> completed) {
        List<FileMove> reverse = new ArrayList<>(completed);
        Collections.reverse(reverse);
        for (FileMove move : reverse) {
            try {
                if (Files.exists(move.target())) storage.move(move.target(), move.source());
            } catch (IOException ignored) {
                // The original exception remains the primary failure.
            }
        }
    }

    private void validateDate(int date) {
        if (!String.valueOf(date).matches("\\d{8}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "日期格式不正确");
        }
    }

    private String relative(Path path) {
        return storage.root().relativize(path).toString().replace('\\', '/');
    }

    private record FileMove(Path source, Path target) {}

    public record TransferRequest(
        String sourceAlbumKey,
        int sourceDate,
        String targetAlbumKey,
        int targetDate,
        String description
    ) {}
}
