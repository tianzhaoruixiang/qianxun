package com.qianxun.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把千寻本轮消息整理成 Dashboard {@code /api/ws} 的一回合：
 * 长程目标走原生 {@code slash.exec /goal}，技能走原生 {@code /{slug} [任务]}
 *（官方经 {@code command.dispatch} 展开），子智能体状态走原生 {@code /agents}，
 * 正文走 {@code prompt.submit}。
 */
public final class ChatDashboardTurn {

    public record Plan(
            String slashCommand,
            boolean expectSendThenPrompt,
            String promptText,
            List<Map<String, String>> seedHistory
    ) {
        public boolean hasSlash() {
            return slashCommand != null && !slashCommand.isBlank();
        }
    }

    private ChatDashboardTurn() {}

    public static Plan plan(
            boolean clearGoal,
            boolean kickoff,
            ChatGoalInvocation.Goal goal,
            List<Map<String, String>> storedHistory,
            List<Map<String, String>> turnMessages
    ) {
        return plan(clearGoal, kickoff, goal, null, false, null, storedHistory, turnMessages);
    }

    public static Plan plan(
            boolean clearGoal,
            boolean kickoff,
            ChatGoalInvocation.Goal goal,
            String skillName,
            List<Map<String, String>> storedHistory,
            List<Map<String, String>> turnMessages
    ) {
        return plan(clearGoal, kickoff, goal, skillName, false, null, storedHistory, turnMessages);
    }

    public static Plan plan(
            boolean clearGoal,
            boolean kickoff,
            ChatGoalInvocation.Goal goal,
            String skillName,
            boolean agentsStatus,
            List<Map<String, String>> storedHistory,
            List<Map<String, String>> turnMessages
    ) {
        return plan(clearGoal, kickoff, goal, skillName, agentsStatus, null, storedHistory, turnMessages);
    }

    public static Plan plan(
            boolean clearGoal,
            boolean kickoff,
            ChatGoalInvocation.Goal goal,
            String skillName,
            boolean agentsStatus,
            String explicitSlash,
            List<Map<String, String>> storedHistory,
            List<Map<String, String>> turnMessages
    ) {
        List<Map<String, String>> seed = withoutLastUser(storedHistory);
        String prompt = lastUserContent(turnMessages);
        String extra = extraSystemText(turnMessages, storedHistory);
        if (!extra.isBlank()) {
            prompt = extra + "\n\n" + prompt;
        }
        if (clearGoal) {
            return new Plan(ChatGoalInvocation.HERMES_CLEAR_COMMAND, false, prompt, seed);
        }
        if (agentsStatus) {
            return new Plan(ChatAgentsInvocation.formatHermesCommand(), false, prompt, seed);
        }
        String passthrough = explicitSlash == null ? "" : explicitSlash.trim();
        if (passthrough.isBlank()) {
            passthrough = ChatSlashCommandSupport.extractPassthroughSlash(prompt);
        }
        if (!passthrough.isBlank() && ChatSlashCommandSupport.isPassthroughOnly(passthrough)) {
            return new Plan(passthrough, false, prompt, seed);
        }
        if (kickoff && goal != null && !goal.isBlank()) {
            return new Plan(ChatGoalInvocation.formatHermesCommand(goal), true, prompt, seed);
        }
        String skill = skillName == null ? "" : skillName.trim();
        if (!skill.isBlank()) {
            // 上游返回 type=skill + 已展开 message 再 submit；勿 expectSendThenPrompt，避免重复下发未展开正文
            return new Plan(ChatSkillInvocation.formatHermesCommand(skill, prompt), false, prompt, seed);
        }
        return new Plan(null, false, prompt, seed);
    }

    static String lastUserContent(List<Map<String, String>> messages) {
        if (messages == null) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, String> m = messages.get(i);
            if (m != null && "user".equals(m.get("role"))) {
                String c = m.get("content");
                return c == null ? "" : c;
            }
        }
        return "";
    }

    static List<Map<String, String>> withoutLastUser(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int lastUser = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, String> m = messages.get(i);
            if (m != null && "user".equals(m.get("role"))) {
                lastUser = i;
                break;
            }
        }
        if (lastUser < 0) {
            return List.copyOf(messages);
        }
        List<Map<String, String>> out = new ArrayList<>(lastUser);
        for (int i = 0; i < lastUser; i++) {
            Map<String, String> m = messages.get(i);
            if (m != null) {
                out.add(m);
            }
        }
        return List.copyOf(out);
    }

    static String extraSystemText(
            List<Map<String, String>> turnMessages,
            List<Map<String, String>> storedHistory
    ) {
        List<String> stored = systemContents(storedHistory);
        StringBuilder sb = new StringBuilder();
        if (turnMessages == null) {
            return "";
        }
        for (Map<String, String> m : turnMessages) {
            if (m == null || !"system".equals(m.get("role"))) {
                continue;
            }
            String c = m.get("content");
            if (c == null || c.isBlank() || stored.contains(c)) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(c.trim());
        }
        return sb.toString();
    }

    private static List<String> systemContents(List<Map<String, String>> messages) {
        List<String> out = new ArrayList<>();
        if (messages == null) {
            return out;
        }
        for (Map<String, String> m : messages) {
            if (m != null && "system".equals(m.get("role")) && m.get("content") != null) {
                out.add(m.get("content"));
            }
        }
        return out;
    }
}
