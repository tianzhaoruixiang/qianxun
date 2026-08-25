package com.qianxun.context;

import com.qianxun.security.UserRoles;

/**
 * 请求级用户上下文（ThreadLocal）。
 * 由 JwtAuthenticationFilter / UserContextInterceptor 在请求开始时设置，请求结束时清除。
 * 账号持久化在 TiDB {@code app_user}；JWT 与请求头只携带当前请求身份。
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
    private static final ThreadLocal<String> ROLE_HOLDER         = new ThreadLocal<>();

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
        if (display != null && !display.isBlank()) {
            return display;
        }
        String name = getCurrentUserName();
        return name.equals(DEFAULT_USER_NAME) ? DEFAULT_DISPLAY : name;
    }

    public static String getCurrentRole() {
        String role = ROLE_HOLDER.get();
        if (role != null && !role.isBlank()) {
            return UserRoles.normalize(role);
        }
        if (DEFAULT_USER_ID.equals(getCurrentUserId())
                || DEFAULT_USER_NAME.equalsIgnoreCase(getCurrentUserName())) {
            return UserRoles.ADMIN;
        }
        return UserRoles.FUNCTIONAL;
    }

    public static boolean isAdmin() {
        return UserRoles.isAdmin(getCurrentRole());
    }

    /** 仅供鉴权过滤器 / UserContextInterceptor 调用 */
    public static void set(String userId, String userName, String displayName) {
        set(userId, userName, displayName, null);
    }

    public static void set(String userId, String userName, String displayName, String role) {
        USER_ID_HOLDER.set(userId != null ? userId.trim() : DEFAULT_USER_ID);
        USER_NAME_HOLDER.set(userName != null ? userName.trim() : DEFAULT_USER_NAME);
        DISPLAY_NAME_HOLDER.set(displayName != null ? displayName.trim() : null);
        if (role != null && !role.isBlank()) {
            ROLE_HOLDER.set(role.trim());
        } else {
            ROLE_HOLDER.remove();
        }
    }

    /** 仅供 UserContextInterceptor 调用 */
    public static void clear() {
        USER_ID_HOLDER.remove();
        USER_NAME_HOLDER.remove();
        DISPLAY_NAME_HOLDER.remove();
        ROLE_HOLDER.remove();
    }
}
