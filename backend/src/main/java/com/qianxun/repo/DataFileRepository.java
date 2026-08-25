package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.DataFile;
import com.qianxun.storage.FolderPaths;
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

    private static final String COLUMNS =
            "`id`,`user_id`,`name`,`display_date`,`kind`,`detail_text`,`detail_json`,"
                    + "`object_key`,`content_type`,`size_bytes`,`public_token`,`folder_path`,`created_at`,`updated_at`";

    private static final RowMapper<DataFile> ROW_MAPPER = (rs, rowNum) -> new DataFile(
            rs.getString("id"),
            rs.getString("user_id"),
            rs.getString("name"),
            rs.getString("display_date"),
            rs.getString("kind"),
            rs.getString("detail_text"),
            rs.getString("detail_json"),
            rs.getString("object_key"),
            rs.getString("content_type"),
            rs.getObject("size_bytes") == null ? null : rs.getLong("size_bytes"),
            rs.getString("public_token"),
            FolderPaths.normalize(rs.getString("folder_path")),
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
                "SELECT " + COLUMNS + " FROM " + table
                        + " ORDER BY `display_date` DESC, `created_at` DESC LIMIT ?",
                ROW_MAPPER, limit
        );
    }

    public List<DataFile> listByUserIdOrderByDateDesc(String userId, int limit) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM " + table
                        + " WHERE `user_id` = ? ORDER BY `kind` = 'folder' DESC, `name` ASC, `created_at` DESC LIMIT ?",
                ROW_MAPPER, userId, limit
        );
    }

    public List<DataFile> listByUserId(String userId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM " + table + " WHERE `user_id` = ?",
                ROW_MAPPER, userId
        );
    }

    public Optional<DataFile> findFolder(String userId, String parentPath, String name) {
        List<DataFile> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM " + table
                        + " WHERE `user_id` = ? AND `kind` = ? AND IFNULL(`folder_path`,'') = ? AND `name` = ? LIMIT 1",
                ROW_MAPPER,
                userId, DataFile.KIND_FOLDER, FolderPaths.normalize(parentPath), name
        );
        return rows.stream().findFirst();
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return n == null ? 0 : n;
    }

    public void insert(DataFile file) {
        jdbc.update(
                "INSERT INTO " + table + " (`id`,`user_id`,`name`,`display_date`,`kind`,`detail_text`,`detail_json`,"
                        + "`object_key`,`content_type`,`size_bytes`,`public_token`,`folder_path`,`created_at`,`updated_at`)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                file.id(), file.userId(), file.name(), file.displayDate(), file.kind(),
                file.detailText(), file.detailJson(), file.objectKey(), file.contentType(),
                file.sizeBytes(), file.publicToken(), FolderPaths.normalize(file.folderPath()),
                Timestamp.from(file.createdAt()), Timestamp.from(file.updatedAt())
        );
    }

    public Optional<DataFile> findById(String id) {
        List<DataFile> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM " + table + " WHERE `id` = ? LIMIT 1",
                ROW_MAPPER,
                id
        );
        return rows.stream().findFirst();
    }

    public Optional<DataFile> findByPublicToken(String token) {
        List<DataFile> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM " + table + " WHERE `public_token` = ? LIMIT 1",
                ROW_MAPPER,
                token
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
        String sql = "SELECT " + COLUMNS + " FROM " + table + " WHERE `id` IN (" + placeholders + ")";
        return jdbc.query(sql, ROW_MAPPER, new ArrayList<>(clean).toArray());
    }

    public void update(DataFile file) {
        jdbc.update(
                "UPDATE " + table + " SET `user_id`=?,`name`=?,`display_date`=?,`kind`=?,`detail_text`=?,"
                        + "`detail_json`=?,`object_key`=?,`content_type`=?,`size_bytes`=?,`public_token`=?,"
                        + "`folder_path`=?,`updated_at`=? WHERE `id`=?",
                file.userId(), file.name(), file.displayDate(), file.kind(), file.detailText(),
                file.detailJson(), file.objectKey(), file.contentType(), file.sizeBytes(),
                file.publicToken(), FolderPaths.normalize(file.folderPath()),
                Timestamp.from(file.updatedAt()), file.id()
        );
    }

    public int deleteById(String id) {
        return jdbc.update("DELETE FROM " + table + " WHERE `id` = ?", id);
    }

    public int deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<String> clean = ids.stream().filter(v -> v != null && !v.isBlank()).distinct().toList();
        if (clean.isEmpty()) {
            return 0;
        }
        String placeholders = clean.stream().map(v -> "?").collect(Collectors.joining(","));
        return jdbc.update(
                "DELETE FROM " + table + " WHERE `id` IN (" + placeholders + ")",
                clean.toArray()
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? Instant.EPOCH : ts.toInstant();
    }
}
