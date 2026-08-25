package com.qianxun.web.dto;

import java.util.Map;

public record UpsertPluginRequest(
        String profile,
        String name,
        String path,
        String version,
        Boolean enabled,
        String description,
        Map<String, Object> manifest
) {}
