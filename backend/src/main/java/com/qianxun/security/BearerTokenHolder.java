package com.qianxun.security;

/**
 * 当前请求的 Bearer，供 Claude Code 网关回调内部编排接口。
 */
public final class BearerTokenHolder {

    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private BearerTokenHolder() {}

    public static void set(String token) {
        if (token == null || token.isBlank()) {
            TOKEN.remove();
        } else {
            TOKEN.set(token.trim());
        }
    }

    public static String get() {
        String t = TOKEN.get();
        return t == null ? "" : t;
    }

    public static void clear() {
        TOKEN.remove();
    }
}
