package com.qianxun.web.interceptor;

import com.qianxun.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 每个 HTTP 请求到达时，从 Header 中读取外部用户系统传入的身份信息并写入 UserContext。
 *
 * 前端使用 encodeURIComponent 对 Header 值编码（支持中文等 non-ISO-8859-1 字符），
 * 此处对应做 URLDecoder.decode 还原。
 *
 * 对接外部系统时，外部系统负责在请求头中注入（URL 编码）：
 *   X-User-Id           —— 用户唯一标识（必须）
 *   X-User-Name         —— 用户登录名（可选）
 *   X-User-Display-Name —— 用户展示名（可选）
 */
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) {
        String userId      = decode(request.getHeader(UserContext.HEADER_USER_ID));
        String userName    = decode(request.getHeader(UserContext.HEADER_USER_NAME));
        String displayName = decode(request.getHeader(UserContext.HEADER_USER_DISPLAY_NAME));
        UserContext.set(userId, userName, displayName);
        return true;
    }

    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            Exception ex
    ) {
        UserContext.clear();
    }

    /** URL-decode header value；null 或空值直接返回原值（由 UserContext 处理默认值）。 */
    private static String decode(String value) {
        if (value == null || value.isEmpty()) return value;
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 未编码的老值（如直接 ASCII 字符串）原样返回
            return value;
        }
    }
}
