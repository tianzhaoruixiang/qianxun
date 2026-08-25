package com.qianxun.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionAgentLabelsTest {

    @Test
    void defaultProfileAndEmptyAgent_shouldBeDigitalOfficer() {
        assertThat(SessionAgentLabels.displayName(null, null, null, null)).isEqualTo("数智干警");
        assertThat(SessionAgentLabels.displayName("", "default", "", "")).isEqualTo("数智干警");
        assertThat(SessionAgentLabels.displayName("", "hermes-agent", null, null)).isEqualTo("数智干警");
        assertThat(SessionAgentLabels.displayName("", "DEFAULT", "default", null)).isEqualTo("数智干警");
    }

    @Test
    void registryChineseName_shouldWin() {
        assertThat(SessionAgentLabels.displayName("intel-bot", "intel-bot", "intel-bot", "情报助手"))
                .isEqualTo("情报助手");
    }

    @Test
    void storedChineseName_usedWhenNoRegistry() {
        assertThat(SessionAgentLabels.displayName("a-code", "a-code", "案件研判", null))
                .isEqualTo("案件研判");
    }

    @Test
    void englishCode_shouldNotBeShown() {
        assertThat(SessionAgentLabels.displayName("worker", "worker", "worker", null))
                .isEqualTo("未分类");
        assertThat(SessionAgentLabels.displayName("", "my-profile", "my-profile", null))
                .isEqualTo("未分类");
    }

    @Test
    void snapshotPrefersRequestedChinese() {
        assertThat(SessionAgentLabels.snapshotName("x", "x", "研判助手", "其它"))
                .isEqualTo("研判助手");
    }
}
