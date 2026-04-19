package com.qianxun.nlu;

import com.qianxun.domain.IntentScenario;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 用户问题的意图识别与槽位抽取结果。
 * scenario 字段在「场景化匹配」时非空，否则为 null（兜底通用问答）。
 */
public record IntentSlotUnderstanding(
        String intent,
        String scenarioCode,
        String scenarioName,
        String agentSkill,
        Map<String, Object> slots,
        List<String> missingRequiredSlots,
        double confidence,
        String reasoning,
        String rawModelText,
        IntentScenario scenario
) {

    public static IntentSlotUnderstanding fallback(String userText, String rawModelText) {
        return new IntentSlotUnderstanding(
                IntentScenario.GENERAL_CODE,
                IntentScenario.GENERAL_CODE,
                "通用问答",
                "",
                Map.of("question", userText == null ? "" : userText),
                List.of(),
                0.0,
                "",
                rawModelText == null ? "" : rawModelText,
                null
        );
    }

    public Map<String, Object> safeSlots() {
        return slots == null ? Collections.emptyMap() : slots;
    }

    public List<String> safeMissingRequiredSlots() {
        return missingRequiredSlots == null ? List.of() : missingRequiredSlots;
    }

    public boolean isGeneral() {
        return scenarioCode == null
                || scenarioCode.isBlank()
                || IntentScenario.GENERAL_CODE.equalsIgnoreCase(scenarioCode);
    }

    public boolean hasMissingRequiredSlot() {
        return !safeMissingRequiredSlots().isEmpty();
    }
}
