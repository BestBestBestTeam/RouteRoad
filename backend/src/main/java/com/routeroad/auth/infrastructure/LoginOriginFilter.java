package com.routeroad.auth.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.util.Set;

final class LoginOriginFilter extends OncePerRequestFilter {
    private final Set<String> origins;
    private final JsonSecurityErrors errors;
    LoginOriginFilter(Set<String> origins, JsonSecurityErrors errors) { this.origins = origins; this.errors = errors; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
        boolean loginPath = "/api/auth/login".equals(request.getServletPath())
                || (request.getContextPath() + "/api/auth/login").equals(request.getRequestURI());
        if ("POST".equals(request.getMethod()) && loginPath) {
            String origin = request.getHeader("Origin");
            if (!allowed(origin, request)) {
                errors.write(response, 403, "INVALID_ORIGIN", "Недопустимый Origin");
                return;
            }
        }
        chain.doFilter(request, response);
    }
    private boolean allowed(String value, HttpServletRequest request) {
        if (value == null || value.equals("null")) return false;
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) return false;
            int port = uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
            return origins.contains(value) || (uri.getScheme().equals(request.getScheme())
                    && uri.getHost().equalsIgnoreCase(request.getServerName()) && port == request.getServerPort());
        } catch (IllegalArgumentException exception) { return false; }
    }
}
