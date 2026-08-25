package com.qianxun.web.dto;

import java.time.Instant;

public record ActivityLogResponse(
        String id,
        String sessionId,
        String userMessageId,
        String assistantMessageId,
        String userContent,
        String llmEndpoint,
        String llmModel,
        String llmRequestJson,
        String llmResponseText,
        String status,
        String errorMessage,
        Long llmDurationMs,
        Long totalDurationMs,
        Instant createdAt
) {}
