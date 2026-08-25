package com.qianxun.web.dto;

import java.util.List;

public record HermesSkillUploadResponse(
        boolean ok,
        List<String> installed,
        List<String> errors
) {}
