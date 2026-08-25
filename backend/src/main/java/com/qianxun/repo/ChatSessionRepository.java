package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.ChatSession;
import org.springframework.beans.factory.annotation.Qualifier;
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
            toInstant(rs.getTimestamp("updated_at")),
            emptyIfNull(rs.getString("agent_code")),
            emptyIfNull(rs.getString("hermes_profile")),
            emptyIfNull(rs.getString("agent_name")),
            emptyIfNull(rs.getString("session_goal"))
    );

    private static final String COLS = "`id`,`user_id`,`title`,`created_at`,`updated_at`,`agent_code`,`hermes_profile`,`agent_name`,`session_goal`";

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    public ChatSessionRepository(@Qualifier("tidbJdbcTemplate") JdbcTemplate jdbcTemplate, QianxunProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = "`" + properties.getDb() + "`.`chat_session`";
    }

    public void insert(ChatSession session) {
        jdbcTemplate.update(
                "INSERT INTO " + table + " (`id`,`user_id`,`title`,`created_at`,`updated_at`,`agent_code`,`hermes_profile`,`agent_name`,`session_goal`) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                session.id(), session.userId(), session.title(),
                Timestamp.from(session.createdAt()), Timestamp.from(session.updatedAt()),
                emptyIfNull(session.agentCode()),
                emptyIfNull(session.hermesProfile()),
                emptyIfNull(session.agentName()),
                emptyIfNull(session.sessionGoal())
        );
    }

    public void updateTitle(String id, String userId, String title, Instant updatedAt) {
        jdbcTemplate.update(
                "UPDATE " + table + " SET `title` = ?, `updated_at` = ? WHERE `id` = ? AND `user_id` = ?",
                title, Timestamp.from(updatedAt), id, userId
        );
    }

    public void updateSessionGoal(String id, String userId, String sessionGoal, Instant updatedAt) {
        jdbcTemplate.update(
                "UPDATE " + table + " SET `session_goal` = ?, `updated_at` = ? WHERE `id` = ? AND `user_id` = ?",
                emptyIfNull(sessionGoal), Timestamp.from(updatedAt), id, userId
        );
    }

    public void touchUpdatedAt(String id, Instant updatedAt) {
        jdbcTemplate.update(
                "UPDATE " + table + " SET `updated_at` = ? WHERE `id` = ?",
                Timestamp.from(updatedAt), id
        );
    }

    /**
     * 仅在尚未写入智能体信息时绑定，避免覆盖历史会话所属智能体。
     */
    public int bindAgentIfEmpty(
            String id,
            String userId,
            String agentCode,
            String hermesProfile,
            String agentName
    ) {
        return jdbcTemplate.update(
                "UPDATE " + table
                        + " SET `agent_code` = ?, `hermes_profile` = ?, `agent_name` = ?"
                        + " WHERE `id` = ? AND `user_id` = ?"
                        + " AND (IFNULL(`agent_code`,'') = '' AND IFNULL(`hermes_profile`,'') = '' AND IFNULL(`agent_name`,'') = '')",
                emptyIfNull(agentCode), emptyIfNull(hermesProfile), emptyIfNull(agentName),
                id, userId
        );
    }

    public int deleteById(String id, String userId) {
        return jdbcTemplate.update(
                "DELETE FROM " + table + " WHERE `id` = ? AND `user_id` = ?", id, userId
        );
    }

    /**
     * 按智能体 code / profile 列出会话。不匹配空 code，也不按 default profile 全量删除。
     */
    public List<String> listIdsByAgent(String agentCode, String hermesProfile) {
        String code = emptyIfNull(agentCode).trim();
        String profile = emptyIfNull(hermesProfile).trim();
        boolean useProfile = !profile.isEmpty() && !isDefaultProfile(profile);
        if (code.isEmpty() && !useProfile) {
            return List.of();
        }
        if (!code.isEmpty() && useProfile) {
            return jdbcTemplate.query(
                    "SELECT `id` FROM " + table + " WHERE `agent_code` = ? OR `hermes_profile` = ?",
                    (rs, n) -> rs.getString("id"),
                    code, profile
            );
        }
        if (useProfile) {
            return jdbcTemplate.query(
                    "SELECT `id` FROM " + table + " WHERE `hermes_profile` = ?",
                    (rs, n) -> rs.getString("id"),
                    profile
            );
        }
        return jdbcTemplate.query(
                "SELECT `id` FROM " + table + " WHERE `agent_code` = ?",
                (rs, n) -> rs.getString("id"),
                code
        );
    }

    public int deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", ids.stream().map(x -> "?").toList());
        return jdbcTemplate.update(
                "DELETE FROM " + table + " WHERE `id` IN (" + placeholders + ")",
                ids.toArray()
        );
    }

    private static boolean isDefaultProfile(String hermesProfile) {
        String p = hermesProfile.trim().toLowerCase();
        return p.isEmpty() || "default".equals(p);
    }

    /** 内部使用（不验证 userId）：供 QianXunServiceChatStream 等内部逻辑使用 */
    public Optional<ChatSession> findById(String id) {
        List<ChatSession> list = jdbcTemplate.query(
                "SELECT " + COLS + " FROM " + table + " WHERE `id` = ? LIMIT 1",
                ROW_MAPPER, id
        );
        return list.stream().findFirst();
    }

    /** 对外 API：同时校验 userId 所有权 */
    public Optional<ChatSession> findByIdAndUserId(String id, String userId) {
        List<ChatSession> list = jdbcTemplate.query(
                "SELECT " + COLS + " FROM " + table
                        + " WHERE `id` = ? AND `user_id` = ? LIMIT 1",
                ROW_MAPPER, id, userId
        );
        return list.stream().findFirst();
    }

    public List<ChatSession> listByUserIdOrderByUpdatedDesc(String userId, int limit) {
        return listByUserIdOrderByUpdatedDesc(userId, limit, 0);
    }

    public List<ChatSession> listByUserIdOrderByUpdatedDesc(String userId, int limit, int offset) {
        return jdbcTemplate.query(
                "SELECT " + COLS + " FROM " + table
                        + " WHERE `user_id` = ? ORDER BY `updated_at` DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, userId, limit, Math.max(0, offset)
        );
    }

    /**
     * 会话列表附带消息条数与最后一条消息预览（用于前端历史会话）。
     */
    public List<ChatSessionWithStats> listByUserIdWithStatsOrderByUpdatedDesc(String userId, int limit) {
        return listByUserIdWithStatsOrderByUpdatedDesc(userId, limit, 0);
    }

    public List<ChatSessionWithStats> listByUserIdWithStatsOrderByUpdatedDesc(String userId, int limit, int offset) {
        String db = table.substring(1, table.indexOf('`', 1));
        String msgTbl = "`" + db + "`.`chat_message`";
        String sql = """
                SELECT s.`id`, s.`user_id`, s.`title`, s.`created_at`, s.`updated_at`,
                  s.`agent_code`, s.`hermes_profile`, s.`agent_name`, s.`session_goal`,
                  (SELECT COUNT(*) FROM %s m WHERE m.`session_id` = s.`id`) AS msg_count,
                  (SELECT m2.`content` FROM %s m2 WHERE m2.`session_id` = s.`id`
                     ORDER BY m2.`created_at` DESC, m2.`id` DESC LIMIT 1) AS last_content
                FROM %s s
                WHERE s.`user_id` = ?
                ORDER BY s.`updated_at` DESC
                LIMIT ? OFFSET ?
                """.formatted(msgTbl, msgTbl, table);
        return jdbcTemplate.query(sql, (rs, rn) -> new ChatSessionWithStats(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("title"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                rs.getLong("msg_count"),
                rs.getString("last_content"),
                emptyIfNull(rs.getString("agent_code")),
                emptyIfNull(rs.getString("hermes_profile")),
                emptyIfNull(rs.getString("agent_name")),
                emptyIfNull(rs.getString("session_goal"))
        ), userId, limit, Math.max(0, offset));
    }

    public record ChatSessionWithStats(
            String id,
            String userId,
            String title,
            Instant createdAt,
            Instant updatedAt,
            long messageCount,
            String lastMessagePreview,
            String agentCode,
            String hermesProfile,
            String agentName,
            String sessionGoal
    ) {}

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? Instant.EPOCH : ts.toInstant();
    }

    private static String emptyIfNull(String s) {
        return s == null ? "" : s;
    }
}
