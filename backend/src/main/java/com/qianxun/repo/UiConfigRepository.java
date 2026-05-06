package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UiConfigRepository {

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    public UiConfigRepository(@Qualifier("tidbJdbcTemplate") JdbcTemplate jdbcTemplate, QianxunProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = "`" + properties.getDb() + "`.`ui_string_config`";
    }

    public Optional<String> findValue(String key) {
        String sql = "SELECT `config_value` FROM " + table + " WHERE `config_key` = ? LIMIT 1";
        var list = jdbcTemplate.query(sql, (rs, rn) -> rs.getString("config_value"), key);
        return list.stream().findFirst().filter(v -> v != null && !v.isBlank());
    }

    public long count() {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return n == null ? 0 : n;
    }

    public void upsert(String key, String value) {
        jdbcTemplate.update(
                "INSERT INTO " + table + " (`config_key`,`config_value`) VALUES (?, ?)"
                        + " ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`)",
                key, value == null ? "" : value
        );
    }
}
