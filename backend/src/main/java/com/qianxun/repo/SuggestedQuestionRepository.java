package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.SuggestedQuestion;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SuggestedQuestionRepository {

    private static final RowMapper<SuggestedQuestion> ROW = (rs, rn) -> new SuggestedQuestion(
            rs.getString("id"),
            rs.getString("text"),
            rs.getString("category") == null ? "" : rs.getString("category"),
            rs.getInt("sort_order"),
            rs.getInt("enabled") != 0
    );

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    public SuggestedQuestionRepository(@Qualifier("tidbJdbcTemplate") JdbcTemplate jdbcTemplate, QianxunProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = "`" + properties.getDb() + "`.`suggested_question`";
    }

    public long count() {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return n == null ? 0 : n;
    }

    public List<SuggestedQuestion> listEnabledOrderBySort() {
        return jdbcTemplate.query(
                "SELECT `id`,`text`,`category`,`sort_order`,`enabled` FROM " + table
                        + " WHERE `enabled` = 1 ORDER BY `sort_order` ASC, `id` ASC",
                ROW
        );
    }

    public void insert(SuggestedQuestion q) {
        jdbcTemplate.update(
                "INSERT INTO " + table + " (`id`,`text`,`category`,`sort_order`,`enabled`) VALUES (?,?,?,?,?)",
                q.id(), q.text(), q.category(), q.sortOrder(), q.enabled() ? 1 : 0
        );
    }
}
