package com.qianxun.service;

/**
 * Hermes 捆绑技能 {@code plan}（斜杠 {@code /plan}）：只写计划到工作区
 * {@code .hermes/plans/}，不执行。执行时改走 {@code subagent-driven-development}。
 * <p>
 * 千寻侧仍经 {@link ChatSkillInvocation} / Dashboard {@code slash.exec} 下发，
 * 本类提供技能名与默认任务文案，避免前后端口径漂移。
 */
public final class ChatPlanInvocation {

    public static final String PLAN_SKILL = "plan";
    public static final String EXECUTE_SKILL = "subagent-driven-development";

    /** 无额外描述时：按对话上下文生成计划 */
    public static final String DEFAULT_CREATE_TASK = "请根据当前对话上下文生成实施计划，并保存到工作区 .hermes/plans/。";

    /** 执行最新计划的默认指令（对齐官方 plan 技能 Execution Handoff） */
    public static final String DEFAULT_EXECUTE_TASK =
            "请执行工作区 .hermes/plans/ 下最新的计划文件："
                    + "按任务逐步推进，每个任务使用独立子智能体（delegate_task），"
                    + "先做规范符合评审再做代码质量评审，两项通过后再进入下一任务。";

    public static final String LOCAL_CREATE_PREFIX = "【生成计划】";
    public static final String LOCAL_EXECUTE_DISPLAY = "【执行计划】按 .hermes/plans/ 最新计划逐步实施";

    private ChatPlanInvocation() {}

    public static boolean isPlanSkill(String skillName) {
        return PLAN_SKILL.equalsIgnoreCase(trim(skillName));
    }

    public static boolean isExecuteSkill(String skillName) {
        return EXECUTE_SKILL.equalsIgnoreCase(trim(skillName));
    }

    public static boolean isPlanRelated(String skillName) {
        return isPlanSkill(skillName) || isExecuteSkill(skillName);
    }

    public static String defaultTaskForSkill(String skillName, String userTask) {
        String task = trim(userTask);
        if (isExecuteSkill(skillName)) {
            if (task.isEmpty()
                    || LOCAL_EXECUTE_DISPLAY.equals(task)
                    || task.startsWith("【执行计划】")) {
                return DEFAULT_EXECUTE_TASK;
            }
            return task;
        }
        if (isPlanSkill(skillName) && task.isEmpty()) {
            return DEFAULT_CREATE_TASK;
        }
        return task;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
