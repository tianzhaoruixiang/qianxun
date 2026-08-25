package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.AppUser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class AppUserRepository {

    private static final RowMapper<AppUser> ROW_MAPPER = (rs, rowNum) -> new AppUser(
            rs.getString("id"),
            rs.getString("username"),
            emptyIfNull(rs.getString("display_name")),
            rs.getString("password_hash"),
            emptyIfNull(rs.getString("role")),
            rs.getBoolean("enabled"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at"))
    );

    private final JdbcTemplate jdbc;
    private final String table;

    public AppUserRepository(@Qualifier("tidbJdbcTemplate") JdbcTemplate jdbc, QianxunProperties properties) {
        this.jdbc = jdbc;
        this.table = "`" + properties.getDb() + "`.`app_user`";
    }

    public Optional<AppUser> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        List<AppUser> rows = jdbc.query(
                "SELECT * FROM " + table + " WHERE `username` = ? LIMIT 1",
                ROW_MAPPER,
                username
        );
        return rows.stream().findFirst();
    }

    public Optional<AppUser> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        List<AppUser> rows = jdbc.query(
                "SELECT * FROM " + table + " WHERE `id` = ? LIMIT 1",
                ROW_MAPPER,
                id
        );
        return rows.stream().findFirst();
    }

    public void insert(AppUser user) {
        jdbc.update(
                "INSERT INTO " + table
                        + " (`id`,`username`,`display_name`,`password_hash`,`role`,`enabled`,`created_at`,`updated_at`)"
                        + " VALUES (?,?,?,?,?,?,?,?)",
                user.id(),
                user.username(),
                user.displayName(),
                user.passwordHash(),
                user.role(),
                user.enabled() ? 1 : 0,
                Timestamp.from(user.createdAt()),
                Timestamp.from(user.updatedAt())
        );
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? Instant.EPOCH : ts.toInstant();
    }
}
