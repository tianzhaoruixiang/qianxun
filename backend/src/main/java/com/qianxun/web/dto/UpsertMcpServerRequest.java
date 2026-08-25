package com.qianxun.web.dto;

import java.util.List;
import java.util.Map;

public record UpsertMcpServerRequest(
        String profile,
        String name,
        String command,
        List<String> args,
        Map<String, String> env,
        Boolean enabled,
        String description,
        String transport,
        String url
) {}
