package com.qianxun.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WelcomeOfficerPresetsTest {

    @Test
    void resolve_shouldKeepStoredSlotsWhenAnyFilled() {
        String[] out = WelcomeOfficerPresets.resolve("只改第一条", "", "  ", List.of("旧推荐"));
        assertThat(out).containsExactly("只改第一条", "", "");
    }

    @Test
    void resolve_shouldFallbackToSuggestedThenDefaults() {
        String[] fromSuggested = WelcomeOfficerPresets.resolve("", "", "", List.of("问A", "问B"));
        assertThat(fromSuggested).containsExactly("问A", "问B", "");

        String[] fromDefaults = WelcomeOfficerPresets.resolve("  ", null, "", List.of());
        assertThat(fromDefaults).containsExactly(
                WelcomeOfficerPresets.DEFAULT_1,
                WelcomeOfficerPresets.DEFAULT_2,
                WelcomeOfficerPresets.DEFAULT_3
        );
    }

    @Test
    void clip_shouldTrimAndCap() {
        assertThat(WelcomeOfficerPresets.clip("  hi  ")).isEqualTo("hi");
        assertThat(WelcomeOfficerPresets.clip("x".repeat(WelcomeOfficerPresets.MAX_CHARS + 8)))
                .hasSize(WelcomeOfficerPresets.MAX_CHARS);
    }
}
