package com.qianxun.web.dto;

public record FeedbackRequest(
        String feedbackType,
        String feedbackNote
) {}
