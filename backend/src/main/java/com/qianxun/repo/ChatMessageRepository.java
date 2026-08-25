package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.ChatMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
            rs.getString("intent_analysis"),
            rs.getString("tool_calls"),
            rs.getString("usage_json"),
            rs.getString("suggestions_json"),
            toInstant(rs.getTimestamp("created_at")),
            rs.getString("status"),
            rs.getString("run_id")
    );

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    public ChatMessageRepository(@Qualifier("tidbJdbcTemplate") JdbcTemplate jdbcTemplate, QianxunProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = "`" + properties.getDb() + "`.`chat_message`";
    }

    public void insert(ChatMessage message) {
        String status = message.status() == null || message.status().isBlank()
                ? ChatMessage.STATUS_COMPLETED
                : message.status();
        jdbcTemplate.update(
                "INSERT INTO " + table
                + " (`id`,`session_id`,`role`,`content`,`thinking_mode`,`think_content`,`entity_cards`,`intent_analysis`,"
                + "`tool_calls`,`usage_json`,`suggestions_json`,`created_at`,`status`,`run_id`)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                message.id(), message.sessionId(), message.role(), message.content(),
                message.thinkingMode(), message.thinkContent(), message.entityCardsJson(),
                message.intentAnalysisJson(), message.toolCallsJson(), message.usageJson(),
                message.suggestionsJson(),
                Timestamp.from(message.createdAt()),
                status,
                message.runId()
        );
    }

    public void updateAssistantContent(
            String id,
            String content,
            String toolCallsJson,
            String usageJson,
            String suggestionsJson,
            String status
    ) {
        jdbcTemplate.update(
                "UPDATE " + table
                + " SET `content` = ?, `tool_calls` = ?, `usage_json` = ?, `suggestions_json` = ?, `status` = ?"
                + " WHERE `id` = ?",
                content,
                toolCallsJson,
                usageJson,
                suggestionsJson,
                status == null || status.isBlank() ? ChatMessage.STATUS_COMPLETED : status,
                id
        );
    }

    /**
     * 按时间正序返回会话消息；若总数超过 {@code limit}，取<strong>最近</strong> {@code limit} 条
     *（再翻成正序），避免超长会话丢掉本轮及近期上下文。
     * <p>
     * 同一秒内：{@code user} 排在 {@code assistant} 之前（与写入时草稿时间对齐策略一致）。
     */
    public List<ChatMessage> listBySessionOrderByCreatedAsc(String sessionId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        // 先按时间倒序取最近 limit 条，再 reverse 成正序给 LLM / 前端
        List<ChatMessage> newestFirst = jdbcTemplate.query(
                "SELECT `id`,`session_id`,`role`,`content`,`thinking_mode`,`think_content`,`entity_cards`,`intent_analysis`,"
                + "`tool_calls`,`usage_json`,`suggestions_json`,`created_at`,`status`,`run_id`"
                + " FROM " + table
                + " WHERE `session_id` = ? ORDER BY `created_at` DESC,"
                + " CASE `role` WHEN 'user' THEN 0 WHEN 'assistant' THEN 1 ELSE 2 END DESC,"
                + " `id` DESC LIMIT ?",
                ROW_MAPPER, sessionId, limit
        );
        if (newestFirst.size() <= 1) {
            return newestFirst;
        }
        List<ChatMessage> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return List.copyOf(chronological);
    }

    public int deleteBySessionId(String sessionId) {
        return jdbcTemplate.update("DELETE FROM " + table + " WHERE `session_id` = ?", sessionId);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? Instant.EPOCH : ts.toInstant();
    }
}
