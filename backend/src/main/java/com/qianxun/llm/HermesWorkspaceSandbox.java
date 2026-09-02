package com.qianxun.llm;

/**
 * 千寻用户在 Claude Code 运行器下的目录约定。
 * <p>
 * 每个用户独占 {@code /opt/data/{userId}/}：
 * <ul>
 *   <li>{@code profiles/{profileName}/} — CLAUDE.md、技能、子智能体 profile（会话间共享）</li>
 *   <li>{@code workspace/} — 用户工作区根，不再作为 cwd</li>
 *   <li>{@code workspace/qx/{sessionId}/} — 每个用户可见多轮会话的独立 cwd</li>
 * </ul>
 */
public final class HermesWorkspaceSandbox {

    public static final String DEFAULT_HOME = "/opt/data";
    public static final String SESSION_DIR = "qx";

    private HermesWorkspaceSandbox() {}

    public static String userRoot(String qianxunUserId) {
        return userRoot(DEFAULT_HOME, qianxunUserId);
    }

    public static String userRoot(String dataDir, String qianxunUserId) {
        String uid = sanitizeOwnerId(qianxunUserId);
        if (uid.isBlank()) {
            throw new IllegalArgumentException("用户标识无效，无法分配数据目录");
        }
        return trimSlash(dataDir == null || dataDir.isBlank() ? DEFAULT_HOME : dataDir) + "/" + uid;
    }

    public static String resolve(String hermesProfile, String qianxunUserId) {
        return workspacePath(DEFAULT_HOME, qianxunUserId);
    }

    /**
     * @param hermesProfile 已忽略，保留三参数签名兼容旧调用
     */
    public static String resolve(String dataDir, String hermesProfile, String qianxunUserId) {
        return workspacePath(dataDir, qianxunUserId);
    }

    public static String workspace(String qianxunUserId) {
        return workspacePath(DEFAULT_HOME, qianxunUserId);
    }

    public static String workspace(String dataDir, String qianxunUserId) {
        return workspacePath(dataDir, qianxunUserId);
    }

    private static String workspacePath(String dataDir, String qianxunUserId) {
        return userRoot(dataDir, qianxunUserId) + "/workspace";
    }

    /**
     * 会话 cwd：{@code /opt/data/{userId}/workspace/qx/{workspaceSessionId}}。
     * {@code workspaceSessionId} 应是用户可见会话 id（task-* 须先解析到父会话）。
     */
    public static String sessionCwd(String qianxunUserId, String workspaceSessionId) {
        return sessionCwd(DEFAULT_HOME, qianxunUserId, workspaceSessionId);
    }

    public static String sessionCwd(String dataDir, String qianxunUserId, String workspaceSessionId) {
        String sid = sanitizeSessionId(workspaceSessionId);
        if (sid.isBlank()) {
            sid = "default";
        }
        return workspacePath(dataDir, qianxunUserId) + "/" + SESSION_DIR + "/" + sid;
    }

    /**
     * 决定文件沙箱归属：普通会话用自身 id；{@code task-*} 用已解析的父会话 id。
     */
    public static String workspaceSessionId(String sessionId, String parentSessionId) {
        String sid = sessionId == null ? "" : sessionId.trim();
        String parent = parentSessionId == null ? "" : parentSessionId.trim();
        if (sid.startsWith("task-") && !parent.isEmpty()) {
            return parent;
        }
        return sid;
    }

    public static String profileHome(String qianxunUserId, String hermesProfile) {
        return profileHome(DEFAULT_HOME, qianxunUserId, hermesProfile);
    }

    public static String profileHome(String dataDir, String qianxunUserId, String hermesProfile) {
        String uid = sanitizeOwnerId(qianxunUserId);
        if (uid.isBlank()) {
            throw new IllegalArgumentException("用户标识无效，无法分配 profile 目录");
        }
        String name = ClaudeCodePaths.normalizeProfileName(hermesProfile);
        return userRoot(dataDir, uid) + "/profiles/" + name;
    }

    static String trimSlash(String path) {
        if (path == null || path.isBlank()) {
            return DEFAULT_HOME;
        }
        String s = path.trim().replace('\\', '/');
        while (s.endsWith("/") && s.length() > 1) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** 仅允许 UUID / hex 类标识，防止路径穿越。 */
    public static String sanitizeOwnerId(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty() || s.length() > 64) {
            return "";
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_';
            if (!ok) {
                return "";
            }
        }
        return s;
    }

    /** 会话目录名：与用户 id 相同的安全字符集。 */
    public static String sanitizeSessionId(String raw) {
        return sanitizeOwnerId(raw);
    }
}
