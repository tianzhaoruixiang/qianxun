package com.qianxun.llm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUsageMergeTest {

    @Test
    void firstEvent_shouldPassThroughUpstream() {
        var usage = new OpenAiCompatibleStreamClient.TokenUsage(100, 20, 120, 128000);
        Map<String, Object> data = TokenUsageMerge.accumulate(null, usage, 0);
        assertThat(data.get("promptTokens")).isEqualTo(100);
        assertThat(data.get("completionTokens")).isEqualTo(20);
        assertThat(data.get("totalTokens")).isEqualTo(120);
        assertThat(data.get("contextUsed")).isEqualTo(100);
        assertThat(data.get("contextWindow")).isEqualTo(128000);
    }

    @Test
    void multipleEvents_shouldSumBillingAndKeepLatestPromptAsContext() {
        var first = new OpenAiCompatibleStreamClient.TokenUsage(50, 10, 60, 128000);
        var second = new OpenAiCompatibleStreamClient.TokenUsage(80, 15, 95, 128000);
        Map<String, Object> once = TokenUsageMerge.accumulate(null, first, 0);
        Map<String, Object> twice = TokenUsageMerge.accumulate(once, second, 0);
        assertThat(twice.get("promptTokens")).isEqualTo(130);
        assertThat(twice.get("completionTokens")).isEqualTo(25);
        assertThat(twice.get("totalTokens")).isEqualTo(155);
        assertThat(twice.get("contextUsed")).isEqualTo(80);
    }

    @Test
    void missingTotal_shouldUsePromptPlusCompletion() {
        var usage = new OpenAiCompatibleStreamClient.TokenUsage(10, 5, null, null);
        Map<String, Object> data = TokenUsageMerge.accumulate(null, usage, 4096);
        assertThat(data.get("totalTokens")).isEqualTo(15);
        assertThat(data.get("contextWindow")).isEqualTo(4096);
        assertThat(data.get("contextUsed")).isEqualTo(10);
    }

    @Test
    void nullUsage_shouldKeepPrevious() {
        var usage = new OpenAiCompatibleStreamClient.TokenUsage(1, 1, 2, 100);
        Map<String, Object> prev = TokenUsageMerge.accumulate(null, usage, 0);
        assertThat(TokenUsageMerge.accumulate(prev, null, 0)).isSameAs(prev);
    }

    @Test
    void dashboardSnapshots_shouldReplaceInsteadOfDoubleCounting() {
        var first = new OpenAiCompatibleStreamClient.TokenUsage(
                1000, 100, 1100, 128000, 900, 1.0, true);
        var latest = new OpenAiCompatibleStreamClient.TokenUsage(
                1800, 180, 1980, 128000, 1200, 0.94, true);

        Map<String, Object> once = TokenUsageMerge.accumulate(null, first, 0);
        Map<String, Object> twice = TokenUsageMerge.accumulate(once, latest, 0);

        assertThat(twice.get("promptTokens")).isEqualTo(1800);
        assertThat(twice.get("completionTokens")).isEqualTo(180);
        assertThat(twice.get("totalTokens")).isEqualTo(1980);
        assertThat(twice.get("contextUsed")).isEqualTo(1200);
        assertThat(twice.get("contextPercent")).isEqualTo(0.94);
        assertThat(twice.get("sessionSnapshot")).isEqualTo(true);
    }
}
