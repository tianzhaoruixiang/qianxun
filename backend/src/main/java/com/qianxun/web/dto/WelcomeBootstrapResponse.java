package com.qianxun.web.dto;

import java.util.List;
import java.util.Map;

/**
 * 首页 / 欢迎区 / 工具中文名等可由运营配置的聚合数据（前端不应写死）。
 */
public record WelcomeBootstrapResponse(
        String disclaimer,
        String greeting,
        String capability,
        String recommendLabel,
        String portraitSeriesALabel,
        String portraitSeriesBLabel,
        List<SuggestedQuestionResponse> suggestedQuestions,
        Map<String, String> toolDisplayNames,
        String presetChat1,
        String presetChat2,
        String presetChat3
) {}
