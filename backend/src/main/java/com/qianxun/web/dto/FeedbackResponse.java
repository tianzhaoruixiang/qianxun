package com.qianxun.web.dto;

import java.time.Instant;

public record FeedbackResponse(
        String id,
        String sessionId,
        String messageId,
        String feedbackType,
        String feedbackNote,
        Instant createdAt
) {}
