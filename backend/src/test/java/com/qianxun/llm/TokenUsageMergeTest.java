package com.qianxun.llm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUsageMergeTest {

    @Test
    void firstEvent_shouldPassThroughUpstreamWithoutContextUnlessSnapshot() {
        var usage = new OpenAiCompatibleStreamClient.TokenUsage(100, 20, 120, 128000);
        Map<String, Object> data = TokenUsageMerge.accumulate(null, usage, 0);
        assertThat(data.get("promptTokens")).isEqualTo(100);
        assertThat(data.get("completionTokens")).isEqualTo(20);
        assertThat(data.get("totalTokens")).isEqualTo(120);
        assertThat(data.get("contextUsed")).isNull();
        assertThat(data.get("contextWindow")).isEqualTo(128000);
    }

    @Test
    void multipleEvents_shouldSumBillingWithoutTouchingContext() {
        var first = new OpenAiCompatibleStreamClient.TokenUsage(50, 10, 60, 128000);
        var second = new OpenAiCompatibleStreamClient.TokenUsage(80, 15, 95, 128000);
        Map<String, Object> once = TokenUsageMerge.accumulate(null, first, 0);
        once.put("contextUsed", 5000);
        once.put("contextSnapshot", true);
        Map<String, Object> twice = TokenUsageMerge.accumulate(once, second, 0);
        assertThat(twice.get("promptTokens")).isEqualTo(130);
        assertThat(twice.get("completionTokens")).isEqualTo(25);
        assertThat(twice.get("totalTokens")).isEqualTo(155);
        assertThat(twice.get("contextUsed")).isEqualTo(5000);
        assertThat(twice.get("contextSnapshot")).isEqualTo(true);
    }

    @Test
    void missingTotal_shouldUsePromptPlusCompletion() {
        var usage = new OpenAiCompatibleStreamClient.TokenUsage(10, 5, null, null);
        Map<String, Object> data = TokenUsageMerge.accumulate(null, usage, 4096);
        assertThat(data.get("totalTokens")).isEqualTo(15);
        assertThat(data.get("contextWindow")).isEqualTo(4096);
        assertThat(data.get("contextUsed")).isNull();
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
                1000, 100, 1100, 128000, 900, 1.0, true, false);
        var latest = new OpenAiCompatibleStreamClient.TokenUsage(
                1800, 180, 1980, 128000, 1200, 0.94, true, false);

        Map<String, Object> once = TokenUsageMerge.accumulate(null, first, 0);
        Map<String, Object> twice = TokenUsageMerge.accumulate(once, latest, 0);

        assertThat(twice.get("promptTokens")).isEqualTo(1800);
        assertThat(twice.get("completionTokens")).isEqualTo(180);
        assertThat(twice.get("totalTokens")).isEqualTo(1980);
        assertThat(twice.get("contextUsed")).isEqualTo(1200);
        assertThat(twice.get("contextPercent")).isEqualTo(0.94);
        assertThat(twice.get("sessionSnapshot")).isEqualTo(true);
    }

    @Test
    void liveOccupancy_shouldMarkLiveWithoutChangingContext() {
        var billed = new OpenAiCompatibleStreamClient.TokenUsage(80, 10, 90, 128000);
        Map<String, Object> once = TokenUsageMerge.accumulate(null, billed, 0);
        once.put("contextUsed", 5300);
        once.put("contextSnapshot", true);
        var live = new OpenAiCompatibleStreamClient.TokenUsage(900, 50, 950, 128000, 950, null, false, true);
        Map<String, Object> twice = TokenUsageMerge.accumulate(once, live, 0);
        assertThat(twice.get("promptTokens")).isEqualTo(80);
        assertThat(twice.get("completionTokens")).isEqualTo(10);
        assertThat(twice.get("contextUsed")).isEqualTo(5300);
        assertThat(twice.get("live")).isEqualTo(true);
    }

    @Test
    void merge_shouldKeepGenerationMs() {
        var billed = new OpenAiCompatibleStreamClient.TokenUsage(80, 10, 90, 128000);
        Map<String, Object> once = TokenUsageMerge.accumulate(null, billed, 0);
        once.put("generationMs", 12_000L);
        var second = new OpenAiCompatibleStreamClient.TokenUsage(20, 5, 25, 128000);
        Map<String, Object> twice = TokenUsageMerge.accumulate(once, second, 0);
        assertThat(twice.get("generationMs")).isEqualTo(12_000L);
    }

    @Test
    void contextSnapshot_shouldRefreshContextWithoutDoubleBilling() {
        var billed = new OpenAiCompatibleStreamClient.TokenUsage(80, 10, 90, 128000);
        Map<String, Object> once = TokenUsageMerge.accumulate(null, billed, 0);
        var snap = new OpenAiCompatibleStreamClient.TokenUsage(
                null, null, null, 200_000, 42_000, 21.0, false, false, true,
                null, null, null, 8000, 1000, null);
        Map<String, Object> twice = TokenUsageMerge.accumulate(once, snap, 0);
        assertThat(twice.get("promptTokens")).isEqualTo(80);
        assertThat(twice.get("completionTokens")).isEqualTo(10);
        assertThat(twice.get("contextUsed")).isEqualTo(42_000);
        assertThat(twice.get("contextWindow")).isEqualTo(200_000);
        assertThat(twice.get("contextSnapshot")).isEqualTo(true);
        assertThat(twice.get("cacheReadTokens")).isEqualTo(8000);
    }

    @Test
    void resultExtras_shouldCarryTreeCostAndDuration() {
        var result = new OpenAiCompatibleStreamClient.TokenUsage(
                100, 20, 120, null, 100, null, false, false, false,
                0.05, 180, 40, null, null, 4500L);
        Map<String, Object> data = TokenUsageMerge.accumulate(null, result, 200_000);
        assertThat(data.get("treePromptTokens")).isEqualTo(180);
        assertThat(data.get("treeCompletionTokens")).isEqualTo(40);
        assertThat(data.get("totalCostUsd")).isEqualTo(0.05);
        assertThat(data.get("generationMs")).isEqualTo(4500L);
    }
}
