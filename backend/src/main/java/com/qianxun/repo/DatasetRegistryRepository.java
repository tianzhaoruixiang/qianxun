package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.DatasetRegistryItem;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class DatasetRegistryRepository {

    private static final RowMapper<DatasetRegistryItem> ROW_MAPPER = (rs, rowNum) -> new DatasetRegistryItem(
            rs.getString("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("source_type"),
            rs.getString("source_ref"),
            rs.getInt("doc_count"),
            rs.getBoolean("enabled"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at"))
    );

    private final JdbcTemplate jdbc;
    private final String table;

    public DatasetRegistryRepository(@Qualifier("tidbJdbcTemplate") JdbcTemplate jdbc, QianxunProperties properties) {
        this.jdbc = jdbc;
        this.table = "`" + properties.getDb() + "`.`dataset_registry`";
    }

    public List<DatasetRegistryItem> list(boolean enabledOnly) {
        if (enabledOnly) {
            return jdbc.query(
                    "SELECT * FROM " + table + " WHERE `enabled` = 1 ORDER BY `updated_at` DESC",
                    ROW_MAPPER
            );
        }
        return jdbc.query("SELECT * FROM " + table + " ORDER BY `updated_at` DESC", ROW_MAPPER);
    }

    public Optional<DatasetRegistryItem> findByCode(String code) {
        List<DatasetRegistryItem> rows = jdbc.query(
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

    public void insert(DatasetRegistryItem item) {
        jdbc.update(
                "INSERT INTO " + table + " (`id`,`code`,`name`,`description`,`source_type`,`source_ref`,`doc_count`,`enabled`,`created_at`,`updated_at`) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                item.id(), item.code(), item.name(), item.description(), item.sourceType(),
                item.sourceRef(), item.docCount(), item.enabled(),
                Timestamp.from(item.createdAt()), Timestamp.from(item.updatedAt())
        );
    }

    public void updateByCode(DatasetRegistryItem item) {
        jdbc.update(
                "UPDATE " + table + " SET `name`=?,`description`=?,`source_type`=?,`source_ref`=?,`doc_count`=?,`enabled`=?,`updated_at`=? WHERE `code`=?",
                item.name(), item.description(), item.sourceType(), item.sourceRef(),
                item.docCount(), item.enabled(), Timestamp.from(item.updatedAt()), item.code()
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? Instant.EPOCH : ts.toInstant();
    }
}

