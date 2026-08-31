package com.qianxun.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnownModelContextWindowsTest {

    @Test
    void lookup_qwen36Plus_is1m() {
        assertThat(KnownModelContextWindows.lookup("qwen3.6-plus")).isEqualTo(1_000_000);
        assertThat(KnownModelContextWindows.lookup("openai/qwen3.6-plus")).isEqualTo(1_000_000);
        assertThat(KnownModelContextWindows.lookup("qwen3.6-plus-2026-04-13")).isEqualTo(1_000_000);
    }

    @Test
    void lookup_claudeSonnet_is200k() {
        assertThat(KnownModelContextWindows.lookup("claude-sonnet-4-5")).isEqualTo(200_000);
        assertThat(KnownModelContextWindows.lookup("sonnet")).isEqualTo(200_000);
    }

    @Test
    void lookup_unknown_isZero() {
        assertThat(KnownModelContextWindows.lookup("missing")).isZero();
        assertThat(KnownModelContextWindows.lookup("")).isZero();
        assertThat(KnownModelContextWindows.lookup(null)).isZero();
    }
}
