package com.qianxun.web.dto;

public record UpdateIntentScenarioApiRequest(
        String id,
        UpsertIntentScenarioRequest scenario
) {}
