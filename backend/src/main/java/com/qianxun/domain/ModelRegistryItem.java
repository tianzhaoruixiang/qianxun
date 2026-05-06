package com.qianxun.domain;

import java.time.Instant;

public record ModelRegistryItem(
        String id,
        String code,
        String name,
        String provider,
        String baseUrl,
        int contextWindow,
        int maxTokens,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {}

