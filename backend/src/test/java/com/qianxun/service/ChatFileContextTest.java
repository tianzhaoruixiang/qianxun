package com.qianxun.service;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.DataFile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatFileContextTest {

    @Test
    void apply_withoutAttachments_shouldNotInjectRecentFiles() {
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "你好"));
        DataFile recent = new DataFile(
                "f1", "u1", "huge.txt", "2026-08-15", DataFile.KIND_TEXT,
                "x".repeat(8000), null, null, null, 8000L, "tok", "", Instant.now(), Instant.now());
        List<Map<String, String>> out = ChatFileContext.apply(
                List.of(), List.of(recent), messages, new QianxunProperties());
        assertThat(out).isSameAs(messages);
    }

    @Test
    void apply_withAttachment_shouldInjectOnlyThatFile() {
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "看这个"));
        DataFile attached = new DataFile(
                "a1", "u1", "note.txt", "2026-08-15", DataFile.KIND_TEXT,
                "附件正文", null, null, null, 4L, "tok-a", "", Instant.now(), Instant.now());
        DataFile other = new DataFile(
                "b1", "u1", "old.txt", "2026-08-14", DataFile.KIND_TEXT,
                "不该出现", null, null, null, 4L, "tok-b", "", Instant.now(), Instant.now());
        List<Map<String, String>> out = ChatFileContext.apply(
                List.of("a1"), List.of(attached, other), messages, new QianxunProperties());
        assertThat(out).hasSize(2);
        assertThat(out.get(0).get("role")).isEqualTo("system");
        assertThat(out.get(0).get("content")).contains("note.txt").contains("附件正文").contains("本轮聊天附件").doesNotContain("old.txt");
    }

    @Test
    void apply_nullAttachmentIds_shouldNotInject() {
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "你好"));
        DataFile recent = new DataFile(
                "f1", "u1", "huge.txt", "2026-08-15", DataFile.KIND_TEXT,
                "x".repeat(100), null, null, null, 100L, "tok", "", Instant.now(), Instant.now());
        List<Map<String, String>> out = ChatFileContext.apply(
                null, List.of(recent), messages, new QianxunProperties());
        assertThat(out).isSameAs(messages);
    }
}
