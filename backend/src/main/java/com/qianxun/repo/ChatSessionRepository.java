package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.ChatSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ChatSessionRepository {

    private static final RowMapper<ChatSession> ROW_MAPPER = (rs, rowNum) -> new ChatSession(
            rs.getString("id"),
            rs.getString("user_id"),
            rs.getString("title"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at"))
    );

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    public ChatSessionRepository(JdbcTemplate jdbcTemplate, QianxunProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = "`" + properties.getDb() + "`.`chat_session`";
    }

    public void insert(ChatSession session) {
        jdbcTemplate.update(
                "INSERT INTO " + table + " (`id`,`user_id`,`title`,`created_at`,`updated_at`) VALUES (?,?,?,?,?)",
                session.id(), session.userId(), session.title(),
                Timestamp.from(session.createdAt()), Timestamp.from(session.updatedAt())
        );
    }

    public void updateTitle(String id, String userId, String title, Instant updatedAt) {
        jdbcTemplate.update(
                "UPDATE " + table + " SET `title` = ?, `updated_at` = ? WHERE `id` = ? AND `user_id` = ?",
                title, Timestamp.from(updatedAt), id, userId
        );
    }

    public void touchUpdatedAt(String id, Instant updatedAt) {
        jdbcTemplate.update(
                "UPDATE " + table + " SET `updated_at` = ? WHERE `id` = ?",
                Timestamp.from(updatedAt), id
        );
    }

    public int deleteById(String id, String userId) {
        return jdbcTemplate.update(
                "DELETE FROM " + table + " WHERE `id` = ? AND `user_id` = ?", id, userId
        );
    }

    /** 内部使用（不验证 userId）：供 QianXunChatStreamService 等内部逻辑使用 */
    public Optional<ChatSession> findById(String id) {
        List<ChatSession> list = jdbcTemplate.query(
                "SELECT `id`,`user_id`,`title`,`created_at`,`updated_at` FROM " + table + " WHERE `id` = ? LIMIT 1",
                ROW_MAPPER, id
        );
        return list.stream().findFirst();
    }

    /** 对外 API：同时校验 userId 所有权 */
    public Optional<ChatSession> findByIdAndUserId(String id, String userId) {
        List<ChatSession> list = jdbcTemplate.query(
                "SELECT `id`,`user_id`,`title`,`created_at`,`updated_at` FROM " + table
                        + " WHERE `id` = ? AND `user_id` = ? LIMIT 1",
                ROW_MAPPER, id, userId
        );
        return list.stream().findFirst();
    }

    public List<ChatSession> listByUserIdOrderByUpdatedDesc(String userId, int limit) {
        return jdbcTemplate.query(
                "SELECT `id`,`user_id`,`title`,`created_at`,`updated_at` FROM " + table
                        + " WHERE `user_id` = ? ORDER BY `updated_at` DESC LIMIT ?",
                ROW_MAPPER, userId, limit
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? Instant.EPOCH : ts.toInstant();
    }
}
