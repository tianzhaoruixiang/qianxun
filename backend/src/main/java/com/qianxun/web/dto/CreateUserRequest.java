package com.qianxun.web.dto;

public record CreateUserRequest(
        String username,
        String password,
        String displayName
) {}
