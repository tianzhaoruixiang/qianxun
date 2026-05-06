package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.DataPortraitPoint;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DataPortraitRepository {

    private static final RowMapper<DataPortraitPoint> ROW_MAPPER = (rs, rowNum) -> new DataPortraitPoint(
            rs.getString("id"),
            rs.getString("group_code"),
            rs.getString("unit"),
            rs.getInt("order_index"),
            rs.getString("label"),
            rs.getInt("series_a"),
            rs.getInt("series_b"),
            rs.getBoolean("focused"),
            rs.getString("focus_label")
    );

    private final JdbcTemplate jdbc;
    private final String table;

    public DataPortraitRepository(@Qualifier("tidbJdbcTemplate") JdbcTemplate jdbc, QianxunProperties properties) {
        this.jdbc = jdbc;
        this.table = "`" + properties.getDb() + "`.`data_portrait_point`";
    }

    public List<DataPortraitPoint> listByGroup(String groupCode) {
        return jdbc.query(
                "SELECT `id`,`group_code`,`unit`,`order_index`,`label`,`series_a`,`series_b`,`focused`,`focus_label` "
                        + "FROM " + table + " WHERE `group_code` = ? ORDER BY `order_index` ASC",
                ROW_MAPPER, groupCode
        );
    }

    public long countByGroup(String groupCode) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE `group_code` = ?", Long.class, groupCode);
        return n == null ? 0 : n;
    }

    public void insert(DataPortraitPoint p) {
        jdbc.update(
                "INSERT INTO " + table + " (`id`,`group_code`,`unit`,`order_index`,`label`,`series_a`,`series_b`,`focused`,`focus_label`) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                p.id(), p.groupCode(), p.unit(), p.orderIndex(),
                p.label(), p.seriesA(), p.seriesB(), p.focused(), p.focusLabel()
        );
    }

    public void deleteByGroup(String groupCode) {
        jdbc.update("DELETE FROM " + table + " WHERE `group_code` = ?", groupCode);
    }
}
