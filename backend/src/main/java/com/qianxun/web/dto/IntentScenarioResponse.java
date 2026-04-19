package com.qianxun.web.dto;

import com.qianxun.domain.IntentScenario;
import com.qianxun.domain.IntentScenario.SlotDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record IntentScenarioResponse(
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
    public static IntentScenarioResponse from(IntentScenario s) {
        return new IntentScenarioResponse(
                s.id(),
                s.code(),
                s.name(),
                s.description(),
                s.safeExamples(),
                s.safeSlots(),
                s.agentSkill(),
                s.promptTemplate(),
                s.safeExtraParams(),
                s.priority(),
                s.enabled(),
                s.createdAt(),
                s.updatedAt()
        );
    }
}
