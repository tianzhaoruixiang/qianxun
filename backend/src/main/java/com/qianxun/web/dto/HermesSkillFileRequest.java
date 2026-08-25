package com.qianxun.web.dto;

public record HermesSkillFileRequest(
        String profile,
        String name,
        String path,
        String content,
        Boolean enabled
) {}
