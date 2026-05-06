package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.ToolDisplayName;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ToolDisplayNameRepository {

    private static final RowMapper<ToolDisplayName> ROW = (rs, rn) -> new ToolDisplayName(
            rs.getString("tool_code"),
            rs.getString("display_name"),
            rs.getInt("sort_order")
    );

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    public ToolDisplayNameRepository(@Qualifier("tidbJdbcTemplate") JdbcTemplate jdbcTemplate, QianxunProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = "`" + properties.getDb() + "`.`tool_display_name`";
    }

    public long count() {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return n == null ? 0 : n;
    }

    public List<ToolDisplayName> listOrderBySort() {
        return jdbcTemplate.query(
                "SELECT `tool_code`,`display_name`,`sort_order` FROM " + table + " ORDER BY `sort_order` ASC, `tool_code` ASC",
                ROW
        );
    }

    public void insert(ToolDisplayName row) {
        jdbcTemplate.update(
                "INSERT INTO " + table + " (`tool_code`,`display_name`,`sort_order`) VALUES (?,?,?)"
                        + " ON DUPLICATE KEY UPDATE `display_name` = VALUES(`display_name`), `sort_order` = VALUES(`sort_order`)",
                row.toolCode(), row.displayName(), row.sortOrder()
        );
    }
}
