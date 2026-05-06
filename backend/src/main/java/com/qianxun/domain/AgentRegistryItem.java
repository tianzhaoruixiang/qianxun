package com.qianxun.domain;

import java.time.Instant;

public record AgentRegistryItem(
        String id,
        String code,
        String name,
        String category,
        String description,
        String icon,
        String modelCode,
        String promptTemplate,
        int priority,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {}

