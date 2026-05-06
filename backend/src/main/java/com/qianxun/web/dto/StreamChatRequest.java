package com.qianxun.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record StreamChatRequest(
        @NotBlank String content,
        /** "quick"（默认）或 "deep"（深度思考） */
        String thinkingMode,
        /**
         * 意图澄清后由前端回传：用户选中的场景 code。
         * 非空时跳过 NLU，直接使用该场景，避免重复识别并确保结果正确。
         */
        String confirmedScenarioCode,
        /**
         * 可选：前端从模型表选择的模型 code（model_registry.code）。
         * 非空时优先使用该模型配置（baseUrl/model/provider）。
         */
        String modelCode,
        /**
         * 可选：前端从数据集表选择的数据集 code（dataset_registry.code）。
         * 非空时会将数据集元信息注入系统提示，作为回答上下文约束。
         */
        String datasetCode,
        /**
         * 可选：前端选择的中间数据文件 id 列表。
         */
        List<String> selectedFileIds
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
