package com.qianxun.web.dto;

public record LoginResponse(
        String token,
        long expiresInSeconds,
        String userId,
        String username,
        String displayName,
        String role
) {}
