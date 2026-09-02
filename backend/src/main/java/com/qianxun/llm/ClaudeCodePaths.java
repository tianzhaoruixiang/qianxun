package com.qianxun.llm;

import com.qianxun.config.QianxunProperties;

import java.nio.file.Path;

/**
 * Claude Code 运行器在数据盘上的目录约定：每用户 {@code /opt/data/{userId}/profiles/{name}}
 * 放 {@code CLAUDE.md} 与 {@code .claude/skills}，会话 cwd 为
 * {@code /opt/data/{userId}/workspace/qx/{sessionId}}。
 */
public final class ClaudeCodePaths {

    private ClaudeCodePaths() {}

    public static String dataDir(QianxunProperties properties) {
        if (properties == null || properties.getClaude() == null) {
            return HermesWorkspaceSandbox.DEFAULT_HOME;
        }
        String d = properties.getClaude().getDataDir();
        return HermesWorkspaceSandbox.trimSlash(d);
    }

    public static String profileHome(QianxunProperties properties, String userId, String profile) {
        return HermesWorkspaceSandbox.profileHome(dataDir(properties), userId, profile);
    }

    public static String workspace(QianxunProperties properties, String userId) {
        return HermesWorkspaceSandbox.workspace(dataDir(properties), userId);
    }

    public static String sessionCwd(QianxunProperties properties, String userId, String workspaceSessionId) {
        return HermesWorkspaceSandbox.sessionCwd(dataDir(properties), userId, workspaceSessionId);
    }

    public static Path claudeMd(String profileHome) {
        return Path.of(profileHome, "CLAUDE.md");
    }

    public static Path soulMd(String profileHome) {
        return Path.of(profileHome, "SOUL.md");
    }

    public static Path skillsDir(String profileHome) {
        return Path.of(profileHome, ".claude", "skills");
    }

    public static Path legacySkillsDir(String profileHome) {
        return Path.of(profileHome, "skills");
    }

    public static Path toolsetsFile(String profileHome) {
        return Path.of(profileHome, ".claude", "qianxun-toolsets.json");
    }

    public static Path disabledSkillsFile(String profileHome) {
        return Path.of(profileHome, ".claude", "qianxun-skills-disabled.json");
    }

    public static Path claudeHome(String profileHome) {
        return Path.of(profileHome, ".claude");
    }

    public static Path sessionFile(String workspace, String qianxunSessionId) {
        String id = qianxunSessionId == null ? "" : qianxunSessionId.trim();
        if (id.isEmpty()) {
            id = "default";
        }
        String safe = id.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (safe.length() > 80) {
            safe = safe.substring(0, 80);
        }
        return Path.of(workspace, ".qianxun", "claude-sessions", safe);
    }

    public static Path skillDir(String profileHome, String skillName) {
        String name = HermesAgentClient.sanitizeSkillName(skillName);
        return skillsDir(profileHome).resolve(name);
    }

    public static boolean isDefaultProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return true;
        }
        String p = profile.trim();
        return "default".equalsIgnoreCase(p) || "hermes-agent".equalsIgnoreCase(p)
                || "claude-code".equalsIgnoreCase(p);
    }

    public static String normalizeProfileName(String raw) {
        if (raw == null) {
            return "default";
        }
        String t = raw.trim();
        if (isDefaultProfile(t)) {
            return "default";
        }
        String safe = HermesAgentClient.sanitizeProfileName(t);
        return safe.isBlank() ? "default" : safe;
    }

    public static String modelLabel(QianxunProperties properties) {
        if (properties == null || properties.getClaude() == null) {
            return "claude-sonnet-4-5";
        }
        String m = properties.getClaude().getChatModel();
        if (m == null || m.isBlank()) {
            return "claude-sonnet-4-5";
        }
        return m.trim();
    }
}
