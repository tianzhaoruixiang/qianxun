package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.AgentRegistryItem;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class AgentRegistryRepository {

    private static final RowMapper<AgentRegistryItem> ROW_MAPPER = (rs, rowNum) -> new AgentRegistryItem(
            rs.getString("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("category"),
            rs.getString("description"),
            rs.getString("icon"),
            rs.getString("model_code"),
            rs.getString("prompt_template"),
            rs.getInt("priority"),
            rs.getBoolean("enabled"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at"))
    );

    private final JdbcTemplate jdbc;
    private final String table;

    public AgentRegistryRepository(@Qualifier("tidbJdbcTemplate") JdbcTemplate jdbc, QianxunProperties properties) {
        this.jdbc = jdbc;
        this.table = "`" + properties.getDb() + "`.`agent_registry`";
    }

    public List<AgentRegistryItem> list(boolean enabledOnly) {
        if (enabledOnly) {
            return jdbc.query(
                    "SELECT * FROM " + table + " WHERE `enabled` = 1 ORDER BY `priority` ASC, `updated_at` DESC",
                    ROW_MAPPER
            );
        }
        return jdbc.query(
                "SELECT * FROM " + table + " ORDER BY `priority` ASC, `updated_at` DESC",
                ROW_MAPPER
        );
    }

    public Optional<AgentRegistryItem> findByCode(String code) {
        List<AgentRegistryItem> rows = jdbc.query(
                "SELECT * FROM " + table + " WHERE `code` = ? LIMIT 1",
                ROW_MAPPER,
                code
        );
        return rows.stream().findFirst();
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return n == null ? 0 : n;
    }

    public void insert(AgentRegistryItem item) {
        jdbc.update(
                "INSERT INTO " + table + " (`id`,`code`,`name`,`category`,`description`,`icon`,`model_code`,`prompt_template`,`priority`,`enabled`,`created_at`,`updated_at`) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                item.id(), item.code(), item.name(), item.category(), item.description(),
                item.icon(), item.modelCode(), item.promptTemplate(), item.priority(), item.enabled(),
                Timestamp.from(item.createdAt()), Timestamp.from(item.updatedAt())
        );
    }

    public void updateByCode(AgentRegistryItem item) {
        jdbc.update(
                "UPDATE " + table + " SET `name`=?,`category`=?,`description`=?,`icon`=?,`model_code`=?,`prompt_template`=?,`priority`=?,`enabled`=?,`updated_at`=? WHERE `code`=?",
                item.name(), item.category(), item.description(), item.icon(), item.modelCode(),
                item.promptTemplate(), item.priority(), item.enabled(), Timestamp.from(item.updatedAt()), item.code()
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? Instant.EPOCH : ts.toInstant();
    }
}

