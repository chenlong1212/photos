package com.peachwuhu.app.auth;

import com.peachwuhu.app.common.AppProperties;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public static final String AUTHENTICATED = "authenticated";
    public static final String CURRENT_ALBUM = "currentAlbum";
    private final AppProperties properties;

    public AuthController(AppProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        return Map.of(
            "authenticated", Boolean.TRUE.equals(session.getAttribute(AUTHENTICATED)),
            "currentAlbum", session.getAttribute(CURRENT_ALBUM) == null ? "peachwuhu" : session.getAttribute(CURRENT_ALBUM)
        );
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request, HttpSession session) {
        if (!properties.getAuth().getUsername().equals(request.username())
            || !properties.getAuth().getPassword().equals(request.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        session.setAttribute(AUTHENTICATED, true);
        session.setAttribute(CURRENT_ALBUM, "peachwuhu");
        return Map.of("status", "success", "currentAlbum", "peachwuhu");
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpSession session) {
        session.invalidate();
        return Map.of("status", "success");
    }

    public record LoginRequest(String username, String password) {}
}
