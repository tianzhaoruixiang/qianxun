package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.ChatActivityLog;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ChatActivityLogRepository {

    private static final RowMapper<ChatActivityLog> ROW_MAPPER = (rs, rowNum) ->
            ChatActivityLog.builder()
                    .id(rs.getString("id"))
                    .userId(rs.getString("user_id"))
                    .sessionId(rs.getString("session_id"))
                    .userMessageId(rs.getString("user_message_id"))
                    .assistantMessageId(rs.getString("assistant_message_id"))
                    .userContent(rs.getString("user_content"))
                    .nluIntent(rs.getString("nlu_intent"))
                    .nluScenarioCode(rs.getString("nlu_scenario_code"))
                    .nluScenarioName(rs.getString("nlu_scenario_name"))
                    .nluAgentSkill(rs.getString("nlu_agent_skill"))
                    .nluConfidence(rs.getObject("nlu_confidence", Double.class))
                    .nluSlots(rs.getString("nlu_slots"))
                    .nluMissingSlots(rs.getString("nlu_missing_slots"))
                    .nluReasoning(rs.getString("nlu_reasoning"))
                    .nluRawResponse(rs.getString("nlu_raw_response"))
                    .llmEndpoint(rs.getString("llm_endpoint"))
                    .llmModel(rs.getString("llm_model"))
                    .llmRequestJson(rs.getString("llm_request_json"))
                    .llmResponseText(rs.getString("llm_response_text"))
                    .status(rs.getString("status"))
                    .errorMessage(rs.getString("error_message"))
                    .thinkingMode(rs.getString("thinking_mode"))
                    .thinkContent(rs.getString("think_content"))
                    .nluDurationMs(rs.getObject("nlu_duration_ms", Long.class))
                    .llmDurationMs(rs.getObject("llm_duration_ms", Long.class))
                    .totalDurationMs(rs.getObject("total_duration_ms", Long.class))
                    .createdAt(toInstant(rs.getTimestamp("created_at")))
                    .build();

    private final JdbcTemplate jdbc;
    private final String table;

    public ChatActivityLogRepository(@Qualifier("tidbJdbcTemplate") JdbcTemplate jdbc, QianxunProperties properties) {
        this.jdbc = jdbc;
        this.table = "`" + properties.getDb() + "`.`chat_activity_log`";
    }

    public void insert(ChatActivityLog log) {
        jdbc.update(
                "INSERT INTO " + table + " (`id`,`user_id`,`session_id`,`user_message_id`,`assistant_message_id`," +
                "`user_content`,`nlu_intent`,`nlu_scenario_code`,`nlu_scenario_name`,`nlu_agent_skill`," +
                "`nlu_confidence`,`nlu_slots`,`nlu_missing_slots`,`nlu_reasoning`,`nlu_raw_response`," +
                "`llm_endpoint`,`llm_model`,`llm_request_json`,`llm_response_text`," +
                "`status`,`error_message`,`thinking_mode`,`think_content`," +
                "`nlu_duration_ms`,`llm_duration_ms`,`total_duration_ms`,`created_at`)" +
                " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                log.id(), log.userId(), log.sessionId(), log.userMessageId(), log.assistantMessageId(),
                log.userContent(), log.nluIntent(), log.nluScenarioCode(), log.nluScenarioName(), log.nluAgentSkill(),
                log.nluConfidence(), log.nluSlots(), log.nluMissingSlots(), log.nluReasoning(), log.nluRawResponse(),
                log.llmEndpoint(), log.llmModel(), log.llmRequestJson(), log.llmResponseText(),
                log.status(), log.errorMessage(), log.thinkingMode(), log.thinkContent(),
                log.nluDurationMs(), log.llmDurationMs(), log.totalDurationMs(),
                Timestamp.from(log.createdAt())
        );
    }

    public Optional<ChatActivityLog> findByAssistantMessageId(String assistantMessageId) {
        List<ChatActivityLog> result = jdbc.query(
                "SELECT * FROM " + table + " WHERE `assistant_message_id` = ? LIMIT 1",
                ROW_MAPPER, assistantMessageId
        );
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<ChatActivityLog> listBySessionId(String sessionId, int limit) {
        return jdbc.query(
                "SELECT * FROM " + table + " WHERE `session_id` = ? ORDER BY `created_at` DESC LIMIT ?",
                ROW_MAPPER, sessionId, limit
        );
    }

    public int deleteBySessionId(String sessionId) {
        return jdbc.update("DELETE FROM " + table + " WHERE `session_id` = ?", sessionId);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? Instant.EPOCH : ts.toInstant();
    }
}
