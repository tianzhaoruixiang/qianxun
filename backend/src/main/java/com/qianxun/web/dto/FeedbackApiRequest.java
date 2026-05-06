package com.qianxun.web.dto;

public record FeedbackApiRequest(
        String sessionId,
        String messageId,
        String feedbackType,
        String feedbackNote
) {}
