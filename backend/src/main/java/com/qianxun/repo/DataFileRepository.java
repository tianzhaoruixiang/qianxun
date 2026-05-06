package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.DataFile;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class DataFileRepository {

    private static final RowMapper<DataFile> ROW_MAPPER = (rs, rowNum) -> new DataFile(
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("display_date"),
            rs.getString("kind"),
            rs.getString("detail_text"),
            rs.getString("detail_json"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at"))
    );

    private final JdbcTemplate jdbc;
    private final String table;

    public DataFileRepository(@Qualifier("tidbJdbcTemplate") JdbcTemplate jdbc, QianxunProperties properties) {
        this.jdbc = jdbc;
        this.table = "`" + properties.getDb() + "`.`data_file`";
    }

    public List<DataFile> listOrderByDateDesc(int limit) {
        return jdbc.query(
                "SELECT `id`,`name`,`display_date`,`kind`,`detail_text`,`detail_json`,`created_at`,`updated_at` FROM " + table
                        + " ORDER BY `display_date` DESC, `created_at` DESC LIMIT ?",
                ROW_MAPPER, limit
        );
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return n == null ? 0 : n;
    }

    public void insert(DataFile file) {
        jdbc.update(
                "INSERT INTO " + table + " (`id`,`name`,`display_date`,`kind`,`detail_text`,`detail_json`,`created_at`,`updated_at`) VALUES (?,?,?,?,?,?,?,?)",
                file.id(), file.name(), file.displayDate(), file.kind(), file.detailText(), file.detailJson(),
                Timestamp.from(file.createdAt()), Timestamp.from(file.updatedAt())
        );
    }

    public Optional<DataFile> findById(String id) {
        List<DataFile> rows = jdbc.query(
                "SELECT `id`,`name`,`display_date`,`kind`,`detail_text`,`detail_json`,`created_at`,`updated_at` FROM " + table + " WHERE `id` = ? LIMIT 1",
                ROW_MAPPER,
                id
        );
        return rows.stream().findFirst();
    }

    public List<DataFile> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> clean = ids.stream().filter(v -> v != null && !v.isBlank()).distinct().toList();
        if (clean.isEmpty()) {
            return List.of();
        }
        String placeholders = clean.stream().map(v -> "?").collect(Collectors.joining(","));
        String sql = "SELECT `id`,`name`,`display_date`,`kind`,`detail_text`,`detail_json`,`created_at`,`updated_at` FROM "
                + table + " WHERE `id` IN (" + placeholders + ")";
        return jdbc.query(sql, ROW_MAPPER, new ArrayList<>(clean).toArray());
    }

    public void update(DataFile file) {
        jdbc.update(
                "UPDATE " + table + " SET `name`=?,`display_date`=?,`kind`=?,`detail_text`=?,`detail_json`=?,`updated_at`=? WHERE `id`=?",
                file.name(), file.displayDate(), file.kind(), file.detailText(), file.detailJson(),
                Timestamp.from(file.updatedAt()), file.id()
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? Instant.EPOCH : ts.toInstant();
    }
}
