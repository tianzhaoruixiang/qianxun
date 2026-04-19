package com.qianxun.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 千寻意图场景定义。每个场景对应一种业务调研目标（如机构调研、人物调研），
 * 与 Hermes Agent 的某个「技能」(agentSkill) 或固定提示词 (promptTemplate) 关联。
 */
public record IntentScenario(
        String id,
        String code,
        String name,
        String description,
        List<String> examples,
        List<SlotDefinition> slots,
        String agentSkill,
        String promptTemplate,
        Map<String, Object> extraParams,
        int priority,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {

    public static final String GENERAL_CODE = "general";

    public boolean isGeneral() {
        return GENERAL_CODE.equalsIgnoreCase(code);
    }

    public List<SlotDefinition> safeSlots() {
        return slots == null ? List.of() : slots;
    }

    public List<String> safeExamples() {
        return examples == null ? List.of() : examples;
    }

    public Map<String, Object> safeExtraParams() {
        return extraParams == null ? Map.of() : extraParams;
    }

    /**
     * 槽位定义。
     * type 取值建议：string / number / boolean / enum / date。
     * values 仅在 type=enum 时有意义。
     */
    public record SlotDefinition(
            String name,
            String type,
            boolean required,
            String description,
            List<String> values
    ) {
        public List<String> safeValues() {
            return values == null ? List.of() : values;
        }
    }
}
