package com.qianxun.web.dto;

import java.time.Instant;

public record ChatMessageResponse(
        String id,
        String sessionId,
        String role,
        String content,
        /** "quick" 或 "deep"，user 消息为 null */
        String thinkingMode,
        /** deep 模式下 AI 的推理内容，user 消息为 null */
        String thinkContent,
        Instant createdAt
) {}
