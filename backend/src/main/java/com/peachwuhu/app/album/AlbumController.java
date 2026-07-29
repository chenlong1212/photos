package com.peachwuhu.app.album;

import com.peachwuhu.app.auth.AuthController;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AlbumController {
    private final AlbumService service;

    public AlbumController(AlbumService service) {
        this.service = service;
    }

    @GetMapping("/albums")
    public List<Map<String, Object>> albums() {
        return service.albums();
    }

    @PostMapping("/albums/{albumKey}/select")
    public Map<String, String> select(@PathVariable String albumKey, HttpSession session) {
        service.requireAlbum(albumKey);
        session.setAttribute(AuthController.CURRENT_ALBUM, albumKey);
        return Map.of("currentAlbum", albumKey);
    }

    @GetMapping("/albums/{albumKey}/timeline")
    public Map<String, Object> timeline(@PathVariable String albumKey) {
        return service.timeline(albumKey);
    }

    @GetMapping("/albums/{albumKey}/days/{date}")
    public Map<String, Object> day(@PathVariable String albumKey, @PathVariable int date) {
        return service.day(albumKey, date);
    }

    @PutMapping("/albums/{albumKey}/days/{date}")
    public Map<String, String> update(
        @PathVariable String albumKey,
        @PathVariable int date,
        @RequestBody UpdateDayRequest request
    ) {
        service.updateDay(albumKey, date, request.info(), request.order(), request.covers());
        return Map.of("status", "success");
    }

    public record UpdateDayRequest(String info, List<Long> order, List<Long> covers) {
        public UpdateDayRequest {
            order = order == null ? List.of() : order;
            covers = covers == null ? List.of() : covers;
        }
    }
}
