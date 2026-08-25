package com.qianxun.domain;

import java.time.Instant;

public record AppUser(
        String id,
        String username,
        String displayName,
        String passwordHash,
        String role,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {}
