package com.qianxun.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
         * 非空时会将数据集元信息注入系统提示；若同时传 {@link #datasetCodes}，以列表为准。
         */
        String datasetCode,
        /**
         * 可选：多数据集 code；顺序保留，去重后与 {@link #datasetCode} 二选一（优先本字段）。
         */
        List<String> datasetCodes,
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

    /** 合并单选与多选字段，去重并保持顺序 */
    public List<String> resolvedDatasetCodes() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (datasetCodes != null) {
            for (String c : datasetCodes) {
                if (c != null && !c.isBlank()) {
                    out.add(c.trim());
                }
            }
        }
        if (!out.isEmpty()) {
            return new ArrayList<>(out);
        }
        if (datasetCode != null && !datasetCode.isBlank()) {
            return List.of(datasetCode.trim());
        }
        return List.of();
    }
}
