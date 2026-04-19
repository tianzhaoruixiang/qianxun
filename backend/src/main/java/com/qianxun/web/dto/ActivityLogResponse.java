package com.qianxun.web.dto;

import java.time.Instant;

public record ActivityLogResponse(
        String id,
        String sessionId,
        String userMessageId,
        String assistantMessageId,
        String userContent,
        String nluIntent,
        String nluScenarioCode,
        String nluScenarioName,
        String nluAgentSkill,
        Double nluConfidence,
        String nluSlots,
        String nluMissingSlots,
        String nluReasoning,
        String llmEndpoint,
        String llmModel,
        String llmRequestJson,
        String llmResponseText,
        String status,
        String errorMessage,
        Long nluDurationMs,
        Long llmDurationMs,
        Long totalDurationMs,
        Instant createdAt
) {}
