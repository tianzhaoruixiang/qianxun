package com.qianxun.domain;

public record SuggestedQuestion(
        String id,
        String text,
        String category,
        int sortOrder,
        boolean enabled
) {}
