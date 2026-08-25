package com.qianxun.web.dto;

public record HermesToolsetToggleRequest(
        String profile,
        String name,
        Boolean enabled
) {}
