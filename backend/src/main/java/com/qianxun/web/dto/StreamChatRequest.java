package com.qianxun.web.dto;

import jakarta.validation.constraints.NotBlank;

public record StreamChatRequest(
        @NotBlank String content,
        /** "quick"（默认）或 "deep"（深度思考） */
        String thinkingMode,
        /**
         * 意图澄清后由前端回传：用户选中的场景 code。
         * 非空时跳过 NLU，直接使用该场景，避免重复识别并确保结果正确。
         */
        String confirmedScenarioCode
) {
    public static final String MODE_QUICK = "quick";
    public static final String MODE_DEEP  = "deep";

    public boolean isDeep() {
        return MODE_DEEP.equalsIgnoreCase(thinkingMode);
    }

    public boolean hasConfirmedScenario() {
        return confirmedScenarioCode != null && !confirmedScenarioCode.isBlank();
    }
}
