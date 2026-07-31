package com.peachwuhu.app.auth;

import com.peachwuhu.app.common.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class TrustedDeviceService {
    private static final String COOKIE_NAME = "peachwuhu_trusted_device";
    private final JdbcTemplate jdbc;
    private final AppProperties properties;
    private final SecureRandom random = new SecureRandom();

    public TrustedDeviceService(JdbcTemplate jdbc, AppProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public void issue(HttpServletRequest request, HttpServletResponse response) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        int days = properties.getAuth().getTrustedDeviceDays();
        jdbc.update("DELETE FROM trusted_devices WHERE expires_at<=NOW()");
        jdbc.update("""
            INSERT INTO trusted_devices(token_hash,user_agent,expires_at)
            VALUES(?,?,DATE_ADD(NOW(), INTERVAL ? DAY))
            """, hash(token), userAgent(request), days);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, Duration.ofDays(days)).toString());
    }

    public boolean authenticate(HttpServletRequest request) {
        String token = token(request);
        if (token == null) return false;
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id FROM trusted_devices
            WHERE token_hash=? AND expires_at>NOW()
            """, hash(token));
        if (rows.isEmpty()) return false;
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(AuthController.AUTHENTICATED, true);
        session.setAttribute(AuthController.CURRENT_ALBUM, "peachwuhu");
        jdbc.update(
            "UPDATE trusted_devices SET last_used_at=NOW(),user_agent=? WHERE id=?",
            userAgent(request),
            rows.get(0).get("id")
        );
        return true;
    }

    public void revoke(HttpServletRequest request, HttpServletResponse response) {
        String token = token(request);
        if (token != null) jdbc.update("DELETE FROM trusted_devices WHERE token_hash=?", hash(token));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(properties.getAuth().isTrustedCookieSecure())
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build();
    }

    private String token(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) return cookie.getValue();
        }
        return null;
    }

    private String userAgent(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        if (value == null) return "";
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前环境不支持 SHA-256", exception);
        }
    }
}
