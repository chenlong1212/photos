package com.peachwuhu.app.auth;

import com.peachwuhu.app.common.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final TrustedDeviceService trustedDevices;

    public AuthController(AppProperties properties, TrustedDeviceService trustedDevices) {
        this.properties = properties;
        this.trustedDevices = trustedDevices;
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        if (!Boolean.TRUE.equals(session.getAttribute(AUTHENTICATED))) trustedDevices.authenticate(request);
        return Map.of(
            "authenticated", Boolean.TRUE.equals(session.getAttribute(AUTHENTICATED)),
            "currentAlbum", session.getAttribute(CURRENT_ALBUM) == null ? "peachwuhu" : session.getAttribute(CURRENT_ALBUM)
        );
    }

    @PostMapping("/login")
    public Map<String, Object> login(
        @RequestBody LoginRequest login,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        if (!properties.getAuth().getUsername().equals(login.username())
            || !properties.getAuth().getPassword().equals(login.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(AUTHENTICATED, true);
        session.setAttribute(CURRENT_ALBUM, "peachwuhu");
        trustedDevices.issue(request, response);
        return Map.of("status", "success", "currentAlbum", "peachwuhu");
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request, HttpServletResponse response) {
        trustedDevices.revoke(request, response);
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return Map.of("status", "success");
    }

    public record LoginRequest(String username, String password) {}
}
