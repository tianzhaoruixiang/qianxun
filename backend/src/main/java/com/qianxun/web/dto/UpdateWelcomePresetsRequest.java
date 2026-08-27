package com.qianxun.web.dto;

public record UpdateWelcomePresetsRequest(
        String presetChat1,
        String presetChat2,
        String presetChat3,
        String officerPortrait
) {}
