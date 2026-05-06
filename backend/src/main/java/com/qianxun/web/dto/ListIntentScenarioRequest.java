package com.qianxun.web.dto;

public record ListIntentScenarioRequest(Boolean enabledOnly) {
    public boolean enabledOnlyValue() {
        return Boolean.TRUE.equals(enabledOnly);
    }
}
