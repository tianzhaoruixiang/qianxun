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
        /** assistant：结构化实体 JSON 数组文本（由模型 qianxun-entities 块解析得到），可为 null */
        String entityCardsJson,
        /** assistant：意图分析 JSON（与 SSE analysis 事件结构一致），可为 null */
        String intentAnalysisJson,
        Instant createdAt
) {
    public static final String MODE_QUICK = "quick";
    public static final String MODE_DEEP  = "deep";
}
