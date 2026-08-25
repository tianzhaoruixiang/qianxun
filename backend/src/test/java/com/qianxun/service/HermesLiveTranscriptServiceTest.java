package com.qianxun.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HermesLiveTranscriptServiceTest {

    @Test
    void parseQuery_shouldAcceptDelegIdAndOptionalTaskIndex() {
        assertThat(HermesLiveTranscriptService.parseQuery(null).delegationId()).isNull();
        assertThat(HermesLiveTranscriptService.parseQuery("").delegationId()).isNull();
        assertThat(HermesLiveTranscriptService.parseQuery("log deleg_cfb92b95").delegationId())
                .isEqualTo("deleg_cfb92b95");
        assertThat(HermesLiveTranscriptService.parseQuery("deleg_cfb92b95 2").taskIndex()).isEqualTo(2);
        assertThat(HermesLiveTranscriptService.parseQuery("/task log deleg_080766e8 0").delegationId())
                .isEqualTo("deleg_080766e8");
        assertThat(HermesLiveTranscriptService.parseQuery("not-a-deleg").delegationId()).isNull();
    }

    @Test
    void sanitizeDelegId_shouldRejectPathTraversal() {
        assertThat(HermesLiveTranscriptService.sanitizeDelegId("../etc")).isEmpty();
        assertThat(HermesLiveTranscriptService.sanitizeDelegId("deleg_cfb92b95")).isEqualTo("deleg_cfb92b95");
        assertThat(HermesLiveTranscriptService.sanitizeDelegId("DELEG_CFB92B95")).isEqualTo("deleg_cfb92b95");
    }

    @Test
    void tail_shouldKeepEndingAndNoteOmission() {
        String text = "aaaa\nbbbb\ncccc";
        String out = HermesLiveTranscriptService.tail(text, 8);
        assertThat(out).contains("省略");
        assertThat(out).endsWith("cccc");
        assertThat(HermesLiveTranscriptService.tail("short", 100)).isEqualTo("short");
    }
}
