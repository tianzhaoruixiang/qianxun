package com.qianxun.domain;

import java.time.Instant;

public record ChatMessage(
        String id,
        String sessionId,
        String role,
        String content,
        /** 仅 assistant 消息有效："quick" 或 "deep" */
        String thinkingMode,
        /** 仅 deep 模式 assistant 消息：<think>...</think> 块的原始内容，已去除标签 */
        String thinkContent,
        Instant createdAt
) {
    public static final String MODE_QUICK = "quick";
    public static final String MODE_DEEP  = "deep";
}
