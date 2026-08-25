package com.qianxun.web.dto;

public record HermesSkillFileNodeResponse(
        String path,
        String name,
        boolean directory,
        Long size,
        boolean text
) {}
