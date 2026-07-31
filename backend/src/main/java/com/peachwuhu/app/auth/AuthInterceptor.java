package com.peachwuhu.app.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final TrustedDeviceService trustedDevices;

    public AuthInterceptor(TrustedDeviceService trustedDevices) {
        this.trustedDevices = trustedDevices;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (Boolean.TRUE.equals(request.getSession(false) == null ? null :
            request.getSession(false).getAttribute(AuthController.AUTHENTICATED))) {
            return true;
        }
        if (trustedDevices.authenticate(request)) return true;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"请先登录\"}");
        return false;
    }
}
