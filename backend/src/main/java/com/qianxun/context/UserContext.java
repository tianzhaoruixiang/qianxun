package com.qianxun.context;

/**
 * 请求级用户上下文（ThreadLocal）。
 * 由 UserContextInterceptor 在请求开始时设置，请求结束时清除。
 * 用户信息完全来自外部系统通过请求头传入，本系统不存储用户数据。
 * SSE 场景下，SSE 工作线程与请求线程不同，必须在 Controller 层提前捕获 userId 后显式传递。
 */
public final class UserContext {

    public static final String HEADER_USER_ID           = "X-User-Id";
    public static final String HEADER_USER_NAME         = "X-User-Name";
    public static final String HEADER_USER_DISPLAY_NAME = "X-User-Display-Name";

    /** 无 Header 时使用的默认回退用户（本地开发 / 未对接外部系统时） */
    public static final String DEFAULT_USER_ID   = "1";
    public static final String DEFAULT_USER_NAME = "admin";
    public static final String DEFAULT_DISPLAY   = "管理员";

    private static final ThreadLocal<String> USER_ID_HOLDER      = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_NAME_HOLDER    = new ThreadLocal<>();
    private static final ThreadLocal<String> DISPLAY_NAME_HOLDER = new ThreadLocal<>();

    private UserContext() {}

    public static String getCurrentUserId() {
        String id = USER_ID_HOLDER.get();
        return (id != null && !id.isBlank()) ? id : DEFAULT_USER_ID;
    }

    public static String getCurrentUserName() {
        String name = USER_NAME_HOLDER.get();
        return (name != null && !name.isBlank()) ? name : DEFAULT_USER_NAME;
    }

    public static String getCurrentDisplayName() {
        String display = DISPLAY_NAME_HOLDER.get();
        if (display != null && !display.isBlank()) return display;
        String name = getCurrentUserName();
        return name.equals(DEFAULT_USER_NAME) ? DEFAULT_DISPLAY : name;
    }

    /** 仅供 UserContextInterceptor 调用 */
    public static void set(String userId, String userName, String displayName) {
        USER_ID_HOLDER.set(userId != null ? userId.trim() : DEFAULT_USER_ID);
        USER_NAME_HOLDER.set(userName != null ? userName.trim() : DEFAULT_USER_NAME);
        DISPLAY_NAME_HOLDER.set(displayName != null ? displayName.trim() : null);
    }

    /** 仅供 UserContextInterceptor 调用 */
    public static void clear() {
        USER_ID_HOLDER.remove();
        USER_NAME_HOLDER.remove();
        DISPLAY_NAME_HOLDER.remove();
    }
}
