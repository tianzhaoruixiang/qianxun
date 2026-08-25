package com.qianxun.web.dto;

public record HermesProfileResponse(
        String name,
        String description,
        String model,
        boolean active,
        String path,
        Integer contextWindow
) {}
