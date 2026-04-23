package com.qianxun.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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
        /** assistant：实体卡片 JSON 数组字符串，无则省略 */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("entityCards")
        String entityCardsJson,
        Instant createdAt
) {}
