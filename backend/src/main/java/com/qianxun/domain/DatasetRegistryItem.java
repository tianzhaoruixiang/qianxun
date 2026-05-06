package com.qianxun.domain;

import java.time.Instant;

public record DatasetRegistryItem(
        String id,
        String code,
        String name,
        String description,
        String sourceType,
        String sourceRef,
        int docCount,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {}

