package com.qianxun.web.dto;

/**
 * 长程目标设定。上游 Claude Code 只认一条完成条件：{@code /goal &lt;condition&gt;}。
 * 本 DTO 是千寻表单拆开的字段，下发前会拼成该条件。
 */
public record SessionGoalRequest(
        String title,
        String description,
        String steps,
        String constraints,
        Integer stopAfterTurns
) {
    public SessionGoalRequest(String title, String description, String steps, String constraints) {
        this(title, description, steps, constraints, null);
    }
}
