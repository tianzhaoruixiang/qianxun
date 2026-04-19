package com.qianxun.web.dto;

import com.qianxun.domain.IntentScenario.SlotDefinition;

import java.util.List;
import java.util.Map;

/**
 * 创建 / 更新意图场景的请求体。
 * 字段可空表示「不修改」（仅在更新场景下生效）；创建时 code/name 必填。
 */
public record UpsertIntentScenarioRequest(
        String code,
        String name,
        String description,
        List<String> examples,
        List<SlotDefinition> slots,
        String agentSkill,
        String promptTemplate,
        Map<String, Object> extraParams,
        Integer priority,
        Boolean enabled
) {
}
