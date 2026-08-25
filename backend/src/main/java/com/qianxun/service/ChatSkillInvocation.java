package com.qianxun.service;

import com.qianxun.llm.HermesAgentClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 本轮强制使用某个已启用技能。
 * <p>
 * Dashboard 路径走原生斜杠 {@code /{skill-slug} [任务]}（与 {@code /goal} 同通道：
 * {@code slash.exec} → 技能场景下官方要求再走 {@code command.dispatch}），由上游展开 SKILL.md。
 * 回退到普通 LLM / mock 时仍用 {@link #apply} 注入说明。
 */
public final class ChatSkillInvocation {

    static final int MAX_SKILL_MD_CHARS = 12_000;
    private static final Pattern INVALID_SLUG_CHARS = Pattern.compile("[^a-z0-9-]+");
    private static final Pattern MULTI_HYPHEN = Pattern.compile("-{2,}");

    private ChatSkillInvocation() {}

    /**
     * 官方 CLI/TUI 技能斜杠：{@code /llm-wiki 写一篇摘要}。
     * slug 规则对齐上游 {@code scan_skill_commands}（小写、空格/下划线→连字符）。
     */
    public static String formatHermesCommand(String skillName, String userInstruction) {
        String slug = toSlashSlug(skillName);
        if (slug.isBlank()) {
            return "";
        }
        String task = userInstruction == null ? "" : userInstruction.trim();
        if (task.isEmpty()) {
            return "/" + slug;
        }
        return "/" + slug + " " + task;
    }

    public static String toSlashSlug(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return "";
        }
        String s = skillName.trim().toLowerCase(Locale.ROOT)
                .replace(' ', '-')
                .replace('_', '-');
        s = INVALID_SLUG_CHARS.matcher(s).replaceAll("");
        s = MULTI_HYPHEN.matcher(s).replaceAll("-");
        return s.replaceAll("^-+", "").replaceAll("-+$", "");
    }

    public static HermesAgentClient.SkillInfo findEnabled(List<HermesAgentClient.SkillInfo> skills, String want) {
        if (skills == null || want == null || want.isBlank()) {
            return null;
        }
        String needle = want.trim();
        for (HermesAgentClient.SkillInfo s : skills) {
            if (s == null || !s.enabled()) {
                continue;
            }
            if (matchesName(s.name(), needle)) {
                return s;
            }
        }
        return null;
    }

    public static boolean exists(List<HermesAgentClient.SkillInfo> skills, String want) {
        if (skills == null || want == null || want.isBlank()) {
            return false;
        }
        String needle = want.trim();
        for (HermesAgentClient.SkillInfo s : skills) {
            if (s != null && matchesName(s.name(), needle)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesName(String skillName, String want) {
        if (skillName == null || want == null) {
            return false;
        }
        if (skillName.trim().equalsIgnoreCase(want.trim())) {
            return true;
        }
        String a = toSlashSlug(skillName);
        String b = toSlashSlug(want);
        return !a.isBlank() && a.equals(b);
    }

    public static String prefixUserTask(String skillName, String userContent) {
        String name = skillName == null ? "" : skillName.trim();
        String task = userContent == null ? "" : userContent.trim();
        if (task.isEmpty()) {
            task = "请按该技能执行。";
        }
        return "请使用技能「" + name + "」完成以下任务：\n" + task;
    }

    public static String systemHint(String skillName, String skillMd) {
        String name = skillName == null ? "" : skillName.trim();
        if (name.isBlank()) {
            return "";
        }
        String body = skillMd == null ? "" : skillMd.trim();
        if (body.length() > MAX_SKILL_MD_CHARS) {
            body = body.substring(0, MAX_SKILL_MD_CHARS) + "\n…（技能说明已截断）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("本轮必须使用技能「").append(name).append("」。");
        sb.append("请阅读并遵循该技能说明，优先用它完成用户任务，不要改用其他技能。");
        if (!body.isBlank()) {
            sb.append("\n\n----- SKILL.md -----\n").append(body);
        }
        return sb.toString();
    }

    public static List<Map<String, String>> apply(
            List<Map<String, String>> messages,
            String skillName,
            String skillMd
    ) {
        List<Map<String, String>> src = messages == null ? List.of() : messages;
        String name = skillName == null ? "" : skillName.trim();
        if (name.isBlank()) {
            return src;
        }
        List<Map<String, String>> out = new ArrayList<>(src.size() + 1);
        String hint = systemHint(name, skillMd);
        if (!hint.isBlank()) {
            out.add(Map.of("role", "system", "content", hint));
        }
        int lastUser = lastUserIndex(src);
        for (int i = 0; i < src.size(); i++) {
            Map<String, String> m = src.get(i);
            if (i == lastUser) {
                out.add(Map.of("role", "user", "content", prefixUserTask(name, m.get("content"))));
            } else {
                out.add(m);
            }
        }
        if (lastUser < 0) {
            out.add(Map.of("role", "user", "content", prefixUserTask(name, "")));
        }
        return out;
    }

    private static int lastUserIndex(List<Map<String, String>> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, String> m = messages.get(i);
            if (m != null && "user".equals(m.get("role"))) {
                return i;
            }
        }
        return -1;
    }

    public static String refuseMessage(boolean exists, String skillName) {
        String name = skillName == null ? "" : skillName.trim();
        if (exists) {
            return "技能「" + name + "」未启用，请在技能市场开启后再使用。";
        }
        return "技能「" + name + "」不存在，或当前智能体未安装该技能。";
    }
}
