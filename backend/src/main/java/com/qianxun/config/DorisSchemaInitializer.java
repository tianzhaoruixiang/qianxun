package com.qianxun.config;

import com.qianxun.domain.IntentScenario;
import com.qianxun.domain.IntentScenario.SlotDefinition;
import com.qianxun.repo.IntentScenarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Order(0)
public class DorisSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DorisSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final QianxunProperties properties;
    private final IntentScenarioRepository intentScenarioRepository;

    public DorisSchemaInitializer(
            JdbcTemplate jdbcTemplate,
            QianxunProperties properties,
            IntentScenarioRepository intentScenarioRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.intentScenarioRepository = intentScenarioRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        String db = properties.getDb();
        log.info("初始化 Doris 库表: {}", db);
        jdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS `" + db + "`");

        // chat_session（含 user_id，user_id 来自外部系统）
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`chat_session` (
                    `id`         VARCHAR(64)   NOT NULL,
                    `user_id`    VARCHAR(64)   NOT NULL DEFAULT "1",
                    `title`      VARCHAR(1024) NOT NULL,
                    `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    `updated_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                UNIQUE KEY(`id`)
                DISTRIBUTED BY HASH(`id`) BUCKETS 8
                PROPERTIES ("replication_allocation" = "tag.location.default: 1")
                """.formatted(db));

        // chat_message（含 thinking_mode / think_content）
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`chat_message` (
                    `id`            VARCHAR(64) NOT NULL,
                    `session_id`    VARCHAR(64) NOT NULL,
                    `role`          VARCHAR(32) NOT NULL,
                    `content`       STRING      NOT NULL,
                    `thinking_mode` VARCHAR(16),
                    `think_content` STRING,
                    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                UNIQUE KEY(`id`)
                DISTRIBUTED BY HASH(`id`) BUCKETS 8
                PROPERTIES ("replication_allocation" = "tag.location.default: 1")
                """.formatted(db));

        // intent_scenario
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`intent_scenario` (
                    `id` VARCHAR(64) NOT NULL,
                    `code` VARCHAR(128) NOT NULL,
                    `name` VARCHAR(256) NOT NULL,
                    `description` STRING,
                    `examples` STRING,
                    `slot_schema` STRING,
                    `agent_skill` VARCHAR(256),
                    `prompt_template` STRING,
                    `extra_params` STRING,
                    `priority` INT NOT NULL DEFAULT "100",
                    `enabled` TINYINT NOT NULL DEFAULT "1",
                    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                UNIQUE KEY(`id`)
                DISTRIBUTED BY HASH(`id`) BUCKETS 4
                PROPERTIES ("replication_allocation" = "tag.location.default: 1")
                """.formatted(db));

        // chat_activity_log（含 user_id）
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`chat_activity_log` (
                    `id`                   VARCHAR(64)  NOT NULL,
                    `user_id`              VARCHAR(64)  NOT NULL DEFAULT "1",
                    `session_id`           VARCHAR(64)  NOT NULL,
                    `user_message_id`      VARCHAR(64),
                    `assistant_message_id` VARCHAR(64),
                    `user_content`         STRING       NOT NULL,
                    `nlu_intent`           VARCHAR(256),
                    `nlu_scenario_code`    VARCHAR(128),
                    `nlu_scenario_name`    VARCHAR(256),
                    `nlu_agent_skill`      VARCHAR(256),
                    `nlu_confidence`       DOUBLE,
                    `nlu_slots`            STRING,
                    `nlu_missing_slots`    STRING,
                    `nlu_reasoning`        STRING,
                    `nlu_raw_response`     STRING,
                    `llm_endpoint`         VARCHAR(512),
                    `llm_model`            VARCHAR(128),
                    `llm_request_json`     STRING,
                    `llm_response_text`    STRING,
                    `status`               VARCHAR(32)  NOT NULL DEFAULT 'success',
                    `error_message`        STRING,
                    `thinking_mode`        VARCHAR(16),
                    `think_content`        STRING,
                    `nlu_duration_ms`      BIGINT,
                    `llm_duration_ms`      BIGINT,
                    `total_duration_ms`    BIGINT,
                    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                UNIQUE KEY(`id`)
                DISTRIBUTED BY HASH(`id`) BUCKETS 8
                PROPERTIES ("replication_allocation" = "tag.location.default: 1")
                """.formatted(db));

        // message_feedback（含 user_id）
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`message_feedback` (
                    `id`              VARCHAR(64)  NOT NULL,
                    `user_id`         VARCHAR(64)  NOT NULL DEFAULT "1",
                    `session_id`      VARCHAR(64)  NOT NULL,
                    `message_id`      VARCHAR(64)  NOT NULL,
                    `activity_log_id` VARCHAR(64),
                    `feedback_type`   VARCHAR(16)  NOT NULL,
                    `feedback_note`   STRING,
                    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                UNIQUE KEY(`id`)
                DISTRIBUTED BY HASH(`id`) BUCKETS 4
                PROPERTIES ("replication_allocation" = "tag.location.default: 1")
                """.formatted(db));

        log.info("Doris 库表就绪");

        // ── 升级迁移：存量表补充 user_id 列（列已存在时静默跳过）──────────────
        tryAlterAddColumn(db, "chat_session",      "user_id",       "VARCHAR(64) NOT NULL DEFAULT \"1\"");
        tryAlterAddColumn(db, "chat_activity_log", "user_id",       "VARCHAR(64) NOT NULL DEFAULT \"1\"");
        tryAlterAddColumn(db, "message_feedback",  "user_id",       "VARCHAR(64) NOT NULL DEFAULT \"1\"");
        tryAlterAddColumn(db, "chat_message",      "thinking_mode", "VARCHAR(16)");
        tryAlterAddColumn(db, "chat_message",      "think_content", "STRING");
        tryAlterAddColumn(db, "chat_activity_log", "thinking_mode", "VARCHAR(16)");
        tryAlterAddColumn(db, "chat_activity_log", "think_content", "STRING");

        seedDefaultScenariosIfEmpty();

        seedDefaultScenariosIfEmpty();    }

    private void tryAlterAddColumn(String db, String table, String column, String columnDef) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE `" + db + "`.`" + table + "` ADD COLUMN `" + column + "` " + columnDef
            );
            log.info("ALTER TABLE {}.{}: 已添加列 {}", db, table, column);
        } catch (Exception ex) {
            log.debug("ALTER TABLE {}.{} ADD COLUMN {} 已跳过（列可能已存在）: {}", db, table, column, ex.getMessage());
        }
    }

    private void seedDefaultScenariosIfEmpty() {
        try {
            long count = intentScenarioRepository.count();
            if (count > 0) {
                log.info("已存在 {} 个意图场景，跳过种子数据", count);
                return;
            }
        } catch (Exception ex) {
            log.warn("意图场景表计数失败，跳过种子: {}", ex.toString());
            return;
        }

        Instant now = Instant.now();
        List<IntentScenario> seeds = List.of(
                buildOrgResearch(now),
                buildPersonResearch(now),
                buildGeneral(now)
        );
        for (IntentScenario s : seeds) {
            try {
                intentScenarioRepository.insert(s);
                log.info("已写入意图场景种子: code={} name={}", s.code(), s.name());
            } catch (Exception ex) {
                log.warn("意图场景种子写入失败 code={}: {}", s.code(), ex.toString());
            }
        }
    }

    private static IntentScenario buildOrgResearch(Instant now) {
        return new IntentScenario(
                newId(),
                "org_research",
                "机构调研",
                "调研某个机构（公司/组织/政府/学校等）的基本信息、业务、财务、风险、领导团队与最新动态。",
                List.of(
                        "帮我调研一下字节跳动",
                        "看看蚂蚁集团最近的财报",
                        "帮我整理一下宁德时代的风险点",
                        "比亚迪最近的业务动态"
                ),
                List.of(
                        new SlotDefinition("org_name", "string", true, "机构名称（公司/组织全称或常用简称）", List.of()),
                        new SlotDefinition("focus", "enum", false, "调研聚焦维度，默认 all",
                                List.of("business", "finance", "news", "risk", "leadership", "all")),
                        new SlotDefinition("time_range", "string", false, "时间范围，如 近一年 / 2024Q4", List.of()),
                        new SlotDefinition("language", "enum", false, "输出语言", List.of("zh", "en"))
                ),
                "org_research_agent",
                """
                        你现在扮演「机构调研专家」，请基于已识别的结构化槽位完成多维度调研报告。
                        - 机构名称：{{org_name}}
                        - 关注维度：{{focus|all}}
                        - 时间范围：{{time_range|不限}}
                        - 输出语言：{{language|zh}}

                        请按以下小节输出（缺失信息显式标注「未公开」或「待补充」）：
                        1. 机构概况（行业、成立时间、规模、办公地点）
                        2. 核心业务与产品
                        3. 财务表现（关键指标、最新报告期）
                        4. 关键事件与新闻（按时间倒序）
                        5. 风险与合规
                        6. 核心管理层
                        7. 值得继续追踪的 5 条高价值线索
                        """,
                Map.of(),
                200,
                true,
                now,
                now
        );
    }

    private static IntentScenario buildPersonResearch(Instant now) {
        return new IntentScenario(
                newId(),
                "person_research",
                "人物调研",
                "调研公众人物（企业家、学者、政府人物等）的基本档案、履历、观点、动态与影响力。",
                List.of(
                        "帮我调研一下张一鸣",
                        "看下黄仁勋最近的言论",
                        "整理一下王兴的履历",
                        "马斯克最近在做什么"
                ),
                List.of(
                        new SlotDefinition("person_name", "string", true, "目标人物姓名", List.of()),
                        new SlotDefinition("affiliation", "string", false, "所属机构/单位（用于消歧）", List.of()),
                        new SlotDefinition("focus", "enum", false, "调研维度",
                                List.of("bio", "career", "publications", "opinions", "news", "network", "all")),
                        new SlotDefinition("time_range", "string", false, "时间范围", List.of())
                ),
                "person_research_agent",
                """
                        你现在扮演「人物调研专家」，请围绕目标人物完成画像式调研。
                        - 人物：{{person_name}}
                        - 所属：{{affiliation|未指定}}
                        - 关注维度：{{focus|all}}
                        - 时间范围：{{time_range|不限}}

                        请按以下小节输出（缺失信息显式标注「未公开」或「待补充」）：
                        1. 基本档案（出生、教育、国籍）
                        2. 职业履历（关键岗位与时间线）
                        3. 关键观点 / 著作 / 演讲
                        4. 近期动态
                        5. 人脉与影响力网络
                        6. 值得继续追踪的 5 条高价值线索
                        """,
                Map.of(),
                190,
                true,
                now,
                now
        );
    }

    private static IntentScenario buildGeneral(Instant now) {
        return new IntentScenario(
                newId(),
                IntentScenario.GENERAL_CODE,
                "通用问答",
                "兜底场景：当用户问题不属于任一专项调研场景时使用，按一般问答处理。",
                List.of(),
                List.of(),
                "",
                "",
                Map.of(),
                0,
                true,
                now,
                now
        );
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
