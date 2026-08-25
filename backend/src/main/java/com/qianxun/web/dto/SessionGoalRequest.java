package com.qianxun.web.dto;

/**
 * 长程目标设定（千寻 /goal）。仅指令文本，不携带工具 schema。
 * 上游 Hermes 收到的是原生 {@code /goal} 斜杠命令，不是本 DTO。
 */
public record SessionGoalRequest(
        String title,
        String description,
        String steps,
        String constraints
) {}
