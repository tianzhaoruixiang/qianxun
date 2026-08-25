package com.qianxun.web.dto;

public record UserResponse(
        String id,
        String username,
        String displayName,
        String avatarUrl,
        boolean enabled,
        String role
) {}
