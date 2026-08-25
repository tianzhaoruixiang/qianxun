package com.qianxun.service;

import com.qianxun.domain.ChatMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QianXunServiceChatStreamSuggestionsTest {

    @Test
    void transcriptIncludesRecentHistoryAndLatestAssistant() {
        Instant now = Instant.parse("2026-08-15T00:00:00Z");
        List<ChatMessage> history = List.of(
                msg("u1", "user", "先介绍一下本案流程", now),
                msg("a1", "assistant", "本案分受理、审查、决定三步。", now),
                msg("u2", "user", "审查阶段要注意什么", now),
                msg("a2", "assistant", "重点核对证据链完整性。", now)
        );
        String transcript = QianXunServiceChatStream.buildSuggestionTranscript(
                history, "审查时建议按时间线整理书证。"
        );
        assertTrue(transcript.contains("用户：审查阶段要注意什么"));
        assertTrue(transcript.contains("助手：审查时建议按时间线整理书证。"));
        assertTrue(transcript.contains("本案分受理"));
    }

    @Test
    void transcriptSkipsBlankAndNonChatRoles() {
        Instant now = Instant.parse("2026-08-15T00:00:00Z");
        List<ChatMessage> history = List.of(
                msg("s1", "system", "hidden", now),
                msg("u1", "user", "   ", now),
                msg("u2", "user", "有效问题", now)
        );
        String transcript = QianXunServiceChatStream.buildSuggestionTranscript(history, "");
        assertTrue(transcript.contains("用户：有效问题"));
        assertFalse(transcript.contains("hidden"));
    }

    private static ChatMessage msg(String id, String role, String content, Instant at) {
        return new ChatMessage(
                id, "sess", role, content,
                null, null, null, null, null, null, null, at,
                ChatMessage.STATUS_COMPLETED, null
        );
    }
}
