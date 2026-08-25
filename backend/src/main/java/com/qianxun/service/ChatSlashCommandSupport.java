package com.qianxun.service;

import java.util.Locale;
import java.util.Set;

/**
 * Claude Code 原生斜杠命令识别与透传。
 */
public final class ChatSlashCommandSupport {

    private static final Set<String> PASSTHROUGH_ROOTS = Set.of(
            "compact", "clear", "help", "memory", "model", "review", "rewind", "cost", "doctor",
            "login", "logout", "permissions", "status", "config", "mcp", "vim", "diff", "export",
            "init", "resume", "stats", "usage", "upgrade", "plan", "agents", "tasks", "task",
            "goal", "skill", "bash", "add-dir", "ide", "pr-comments", "release-notes", "terminal-setup"
    );

    private ChatSlashCommandSupport() {}

    /**
     * 从用户正文提取应下发网关的斜杠命令（含参数）。
     * 已由内建逻辑处理的 goal/skill/agents 由 {@link ChatDashboardTurn} 负责，此处不重复。
     */
    public static String extractPassthroughSlash(String content) {
        if (content == null) {
            return "";
        }
        String t = content.trim();
        if (!t.startsWith("/")) {
            return "";
        }
        int nl = t.indexOf('\n');
        String head = nl >= 0 ? t.substring(0, nl).trim() : t;
        if (head.isBlank()) {
            return "";
        }
        String root = head.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!PASSTHROUGH_ROOTS.contains(root)) {
            // 技能 slug：/{name-with-dash}
            if (root.matches("[a-z0-9]+(?:-[a-z0-9]+)+")) {
                return head;
            }
            return "";
        }
        return head;
    }

    public static boolean isPassthroughOnly(String slash) {
        if (slash == null || slash.isBlank()) {
            return false;
        }
        String root = slash.trim().substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        return PASSTHROUGH_ROOTS.contains(root)
                || root.matches("[a-z0-9]+(?:-[a-z0-9]+)+");
    }
}
