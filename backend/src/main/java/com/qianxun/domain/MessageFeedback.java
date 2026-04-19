package com.qianxun.domain;

import java.time.Instant;

public record MessageFeedback(
        String id,
        String userId,
        String sessionId,
        String messageId,
        String activityLogId,
        String feedbackType,
        String feedbackNote,
        Instant createdAt
) {
    public static final String TYPE_LIKE = "like";
    public static final String TYPE_DISLIKE = "dislike";
}
