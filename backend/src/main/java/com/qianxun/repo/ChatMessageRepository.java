package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.ChatMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class ChatMessageRepository {

    private static final RowMapper<ChatMessage> ROW_MAPPER = (rs, rowNum) -> new ChatMessage(
            rs.getString("id"),
            rs.getString("session_id"),
            rs.getString("role"),
            rs.getString("content"),
            rs.getString("thinking_mode"),
            rs.getString("think_content"),
            rs.getString("entity_cards"),
            toInstant(rs.getTimestamp("created_at"))
    );

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    public ChatMessageRepository(JdbcTemplate jdbcTemplate, QianxunProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = "`" + properties.getDb() + "`.`chat_message`";
    }

    public void insert(ChatMessage message) {
        jdbcTemplate.update(
                "INSERT INTO " + table
                + " (`id`,`session_id`,`role`,`content`,`thinking_mode`,`think_content`,`entity_cards`,`created_at`)"
                + " VALUES (?,?,?,?,?,?,?,?)",
                message.id(), message.sessionId(), message.role(), message.content(),
                message.thinkingMode(), message.thinkContent(), message.entityCardsJson(),
                Timestamp.from(message.createdAt())
        );
    }

    public List<ChatMessage> listBySessionOrderByCreatedAsc(String sessionId, int limit) {
        return jdbcTemplate.query(
                "SELECT `id`,`session_id`,`role`,`content`,`thinking_mode`,`think_content`,`entity_cards`,`created_at`"
                + " FROM " + table
                + " WHERE `session_id` = ? ORDER BY `created_at` ASC, `id` ASC LIMIT ?",
                ROW_MAPPER, sessionId, limit
        );
    }

    public int deleteBySessionId(String sessionId) {
        return jdbcTemplate.update("DELETE FROM " + table + " WHERE `session_id` = ?", sessionId);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? Instant.EPOCH : ts.toInstant();
    }
}
