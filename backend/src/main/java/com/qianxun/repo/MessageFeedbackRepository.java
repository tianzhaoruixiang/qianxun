package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.MessageFeedback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MessageFeedbackRepository {

    private static final RowMapper<MessageFeedback> ROW_MAPPER = (rs, rowNum) -> new MessageFeedback(
            rs.getString("id"),
            rs.getString("user_id"),
            rs.getString("session_id"),
            rs.getString("message_id"),
            rs.getString("activity_log_id"),
            rs.getString("feedback_type"),
            rs.getString("feedback_note"),
            toInstant(rs.getTimestamp("created_at"))
    );

    private final JdbcTemplate jdbc;
    private final String table;

    public MessageFeedbackRepository(JdbcTemplate jdbc, QianxunProperties properties) {
        this.jdbc = jdbc;
        this.table = "`" + properties.getDb() + "`.`message_feedback`";
    }

    public void insert(MessageFeedback feedback) {
        jdbc.update(
                "INSERT INTO " + table +
                " (`id`,`user_id`,`session_id`,`message_id`,`activity_log_id`,`feedback_type`,`feedback_note`,`created_at`)" +
                " VALUES (?,?,?,?,?,?,?,?)",
                feedback.id(), feedback.userId(), feedback.sessionId(), feedback.messageId(),
                feedback.activityLogId(), feedback.feedbackType(), feedback.feedbackNote(),
                Timestamp.from(feedback.createdAt())
        );
    }

    public Optional<MessageFeedback> findByMessageId(String messageId) {
        List<MessageFeedback> result = jdbc.query(
                "SELECT * FROM " + table + " WHERE `message_id` = ? ORDER BY `created_at` DESC LIMIT 1",
                ROW_MAPPER, messageId
        );
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public int deleteByMessageId(String messageId) {
        return jdbc.update("DELETE FROM " + table + " WHERE `message_id` = ?", messageId);
    }

    public int deleteBySessionId(String sessionId) {
        return jdbc.update("DELETE FROM " + table + " WHERE `session_id` = ?", sessionId);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? Instant.EPOCH : ts.toInstant();
    }
}
