package com.qianxun.security;

import com.qianxun.config.QianxunProperties;
import com.qianxun.context.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 校验 {@code Authorization: Bearer &lt;jwt&gt;} 并写入 {@link UserContext}。
 * 关闭 {@code qianxun.auth.enabled} 时不拦截。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final QianxunProperties properties;
    private final JwtService jwtService;

    public JwtAuthenticationFilter(QianxunProperties properties, JwtService jwtService) {
        this.properties = properties;
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!properties.getAuth().isEnabled()) {
            return true;
        }
        String path = stripContextPath(request);
        if (isPublicFileGet(path, request.getMethod())) {
            return true;
        }
        return !path.startsWith("/QianXunService/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.getAuth().isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = stripContextPath(request);
        if (isLoginPath(path, request.getMethod())
                || isHealthPath(path, request.getMethod())
                || isBrandPath(path, request.getMethod())
                || isPublicFileGet(path, request.getMethod())
                || isOpenApiPath(path)
                || isWebSocketUpgrade(path, request)) {
            filterChain.doFilter(request, response);
            return;
        }

        UserContext.clear();

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            writeUnauthorized(response, "未登录或缺少令牌");
            return;
        }
        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            writeUnauthorized(response, "未登录或缺少令牌");
            return;
        }
        try {
            Claims claims = jwtService.parseAndValidate(token);
            String username = claims.getSubject();
            String uid = claims.get(JwtService.CLAIM_USER_ID, String.class);
            if (uid == null || uid.isBlank()) {
                uid = UserContext.DEFAULT_USER_ID;
            }
            String dn = claims.get(JwtService.CLAIM_DISPLAY_NAME, String.class);
            String role = claims.get(JwtService.CLAIM_ROLE, String.class);
            if (username == null || username.isBlank()) {
                username = UserContext.DEFAULT_USER_NAME;
            }
            UserContext.set(uid, username, dn != null && !dn.isBlank() ? dn : null, role);
            request.setAttribute(AuthRequestAttributes.FROM_JWT, Boolean.TRUE);
        } catch (JwtException | IllegalArgumentException e) {
            writeUnauthorized(response, "令牌无效或已过期");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String stripContextPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            return uri.substring(ctx.length());
        }
        return uri;
    }

    private static boolean isLoginPath(String path, String method) {
        return "POST".equalsIgnoreCase(method) && "/QianXunService/auth/login".equals(path);
    }

    private static boolean isHealthPath(String path, String method) {
        return "GET".equalsIgnoreCase(method) && "/QianXunService/auth/health".equals(path);
    }

    private static boolean isBrandPath(String path, String method) {
        return "POST".equalsIgnoreCase(method) && "/QianXunService/welcome/brand".equals(path);
    }

    /** 智能体 / 浏览器通过公开 token 下载用户文档，无需登录。 */
    private static boolean isPublicFileGet(String path, String method) {
        return "GET".equalsIgnoreCase(method) && path.startsWith("/QianXunService/data/files/public/");
    }

    private static boolean isOpenApiPath(String path) {
        return path.startsWith("/QianXunService/swagger-ui")
                || path.startsWith("/QianXunService/v3/api-docs")
                || path.equals("/prometheus")
                || path.equals("/metrics")
                || path.equals("/health")
                || path.startsWith("/actuator/");
    }

    private static boolean isWebSocketUpgrade(String path, jakarta.servlet.http.HttpServletRequest request) {
        return path.startsWith("/QianXunService/ws/")
                && "websocket".equalsIgnoreCase(request.getHeader("Upgrade"));
    }

    private static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        String esc = message.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write("{\"code\":401,\"message\":\"" + esc + "\",\"data\":null}");
    }
}
