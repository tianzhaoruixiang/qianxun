package com.qianxun.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.config.QianxunProperties;
import com.qianxun.domain.IntentScenario;
import com.qianxun.domain.IntentScenario.SlotDefinition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class IntentScenarioRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String table;
    private final RowMapper<IntentScenario> rowMapper;

    public IntentScenarioRepository(@Qualifier("tidbJdbcTemplate") JdbcTemplate jdbc, ObjectMapper mapper, QianxunProperties properties) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.table = "`" + properties.getDb() + "`.`intent_scenario`";
        this.rowMapper = (rs, rowNum) -> new IntentScenario(
                rs.getString("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                readJsonList(rs.getString("examples"), new TypeReference<List<String>>() {
                }),
                readJsonList(rs.getString("slot_schema"), new TypeReference<List<SlotDefinition>>() {
                }),
                rs.getString("agent_skill"),
                rs.getString("prompt_template"),
                readJsonMap(rs.getString("extra_params")),
                rs.getInt("priority"),
                rs.getInt("enabled") == 1,
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at"))
        );
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return n == null ? 0L : n;
    }

    public void insert(IntentScenario s) {
        jdbc.update(
                "INSERT INTO " + table
                        + " (`id`, `code`, `name`, `description`, `examples`, `slot_schema`, "
                        + "`agent_skill`, `prompt_template`, `extra_params`, `priority`, `enabled`, `created_at`, `updated_at`) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                s.id(),
                s.code(),
                s.name(),
                nullToEmpty(s.description()),
                writeJson(s.safeExamples()),
                writeJson(s.safeSlots()),
                nullToEmpty(s.agentSkill()),
                nullToEmpty(s.promptTemplate()),
                writeJson(s.safeExtraParams()),
                s.priority(),
                s.enabled() ? 1 : 0,
                Timestamp.from(s.createdAt()),
                Timestamp.from(s.updatedAt())
        );
    }

    public int updateAll(IntentScenario s) {
        return jdbc.update(
                "UPDATE " + table + " SET "
                        + "`code` = ?, `name` = ?, `description` = ?, `examples` = ?, `slot_schema` = ?, "
                        + "`agent_skill` = ?, `prompt_template` = ?, `extra_params` = ?, "
                        + "`priority` = ?, `enabled` = ?, `updated_at` = ? WHERE `id` = ?",
                s.code(),
                s.name(),
                nullToEmpty(s.description()),
                writeJson(s.safeExamples()),
                writeJson(s.safeSlots()),
                nullToEmpty(s.agentSkill()),
                nullToEmpty(s.promptTemplate()),
                writeJson(s.safeExtraParams()),
                s.priority(),
                s.enabled() ? 1 : 0,
                Timestamp.from(s.updatedAt()),
                s.id()
        );
    }

    public int deleteById(String id) {
        return jdbc.update("DELETE FROM " + table + " WHERE `id` = ?", id);
    }

    public Optional<IntentScenario> findById(String id) {
        List<IntentScenario> list = jdbc.query(
                baseSelect() + " WHERE `id` = ? LIMIT 1",
                rowMapper,
                id
        );
        return list.stream().findFirst();
    }

    public Optional<IntentScenario> findByCode(String code) {
        List<IntentScenario> list = jdbc.query(
                baseSelect() + " WHERE `code` = ? LIMIT 1",
                rowMapper,
                code
        );
        return list.stream().findFirst();
    }

    public List<IntentScenario> listAll() {
        return jdbc.query(baseSelect() + " ORDER BY `priority` DESC, `code` ASC", rowMapper);
    }

    public List<IntentScenario> listEnabledOrderByPriorityDesc() {
        return jdbc.query(
                baseSelect() + " WHERE `enabled` = 1 ORDER BY `priority` DESC, `code` ASC",
                rowMapper
        );
    }

    private String baseSelect() {
        return "SELECT `id`, `code`, `name`, `description`, `examples`, `slot_schema`, "
                + "`agent_skill`, `prompt_template`, `extra_params`, `priority`, `enabled`, `created_at`, `updated_at` "
                + "FROM " + table;
    }

    private <T> List<T> readJsonList(String text, TypeReference<List<T>> ref) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            List<T> list = mapper.readValue(text, ref);
            return list == null ? List.of() : list;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Object> readJsonMap(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> map = mapper.readValue(text, new TypeReference<Map<String, Object>>() {
            });
            return map == null ? Map.of() : map;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? Instant.EPOCH : ts.toInstant();
    }
}
