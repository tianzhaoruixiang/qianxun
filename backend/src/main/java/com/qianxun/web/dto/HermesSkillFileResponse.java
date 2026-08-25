package com.qianxun.web.dto;

public record HermesSkillFileResponse(
        String path,
        String content,
        boolean text,
        String name
) {}
