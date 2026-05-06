package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.ModelRegistryItem;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ModelRegistryRepository {

    private static final RowMapper<ModelRegistryItem> ROW_MAPPER = (rs, rowNum) -> new ModelRegistryItem(
            rs.getString("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("provider"),
            rs.getString("base_url"),
            rs.getInt("context_window"),
            rs.getInt("max_tokens"),
            rs.getBoolean("enabled"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at"))
    );

    private final JdbcTemplate jdbc;
    private final String table;

    public ModelRegistryRepository(@Qualifier("tidbJdbcTemplate") JdbcTemplate jdbc, QianxunProperties properties) {
        this.jdbc = jdbc;
        this.table = "`" + properties.getDb() + "`.`model_registry`";
    }

    public List<ModelRegistryItem> list(boolean enabledOnly) {
        if (enabledOnly) {
            return jdbc.query(
                    "SELECT * FROM " + table + " WHERE `enabled` = 1 ORDER BY `updated_at` DESC",
                    ROW_MAPPER
            );
        }
        return jdbc.query("SELECT * FROM " + table + " ORDER BY `updated_at` DESC", ROW_MAPPER);
    }

    public Optional<ModelRegistryItem> findByCode(String code) {
        List<ModelRegistryItem> rows = jdbc.query(
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

    public void insert(ModelRegistryItem item) {
        jdbc.update(
                "INSERT INTO " + table + " (`id`,`code`,`name`,`provider`,`base_url`,`context_window`,`max_tokens`,`enabled`,`created_at`,`updated_at`) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                item.id(), item.code(), item.name(), item.provider(), item.baseUrl(),
                item.contextWindow(), item.maxTokens(), item.enabled(),
                Timestamp.from(item.createdAt()), Timestamp.from(item.updatedAt())
        );
    }

    public void updateByCode(ModelRegistryItem item) {
        jdbc.update(
                "UPDATE " + table + " SET `name`=?,`provider`=?,`base_url`=?,`context_window`=?,`max_tokens`=?,`enabled`=?,`updated_at`=? WHERE `code`=?",
                item.name(), item.provider(), item.baseUrl(), item.contextWindow(),
                item.maxTokens(), item.enabled(), Timestamp.from(item.updatedAt()), item.code()
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? Instant.EPOCH : ts.toInstant();
    }
}

