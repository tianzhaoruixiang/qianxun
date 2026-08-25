package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ChatMessageRepository repository;

    @BeforeEach
    void setUp() {
        QianxunProperties properties = new QianxunProperties();
        properties.setDb("qx");
        repository = new ChatMessageRepository(jdbcTemplate, properties);
    }

    @Test
    void listBySession_shouldFetchNewestFirstThenReturnChronological() {
        Instant t1 = Instant.parse("2026-01-01T00:00:01Z");
        Instant t2 = Instant.parse("2026-01-01T00:00:02Z");
        Instant t3 = Instant.parse("2026-01-01T00:00:03Z");
        ChatMessage oldest = msg("1", "user", "最早", t1);
        ChatMessage mid = msg("2", "assistant", "中间", t2);
        ChatMessage newest = msg("3", "user", "最近", t3);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("s1"), eq(3)))
                .thenReturn(List.of(newest, mid, oldest));

        List<ChatMessage> out = repository.listBySessionOrderByCreatedAsc("s1", 3);

        assertThat(out).extracting(ChatMessage::id).containsExactly("1", "2", "3");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq("s1"), eq(3));
        assertThat(sql.getValue()).contains("ORDER BY `created_at` DESC");
        assertThat(sql.getValue()).contains("LIMIT ?");
    }

    @Test
    void listBySession_nonPositiveLimit_shouldReturnEmptyWithoutQuery() {
        assertThat(repository.listBySessionOrderByCreatedAsc("s1", 0)).isEmpty();
        assertThat(repository.listBySessionOrderByCreatedAsc("s1", -1)).isEmpty();
    }

    private static ChatMessage msg(String id, String role, String content, Instant at) {
        return new ChatMessage(
                id, "s1", role, content, null, null, null, null, null, null, null, at,
                ChatMessage.STATUS_COMPLETED, null
        );
    }
}
