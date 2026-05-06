package com.qianxun.config;

import com.qianxun.domain.AgentRegistryItem;
import com.qianxun.domain.DataFile;
import com.qianxun.domain.DataPortraitPoint;
import com.qianxun.domain.DatasetRegistryItem;
import com.qianxun.domain.IntentScenario;
import com.qianxun.domain.ModelRegistryItem;
import com.qianxun.domain.SuggestedQuestion;
import com.qianxun.domain.ToolDisplayName;
import com.qianxun.domain.IntentScenario.SlotDefinition;
import com.qianxun.repo.AgentRegistryRepository;
import com.qianxun.repo.DataFileRepository;
import com.qianxun.repo.DataPortraitRepository;
import com.qianxun.repo.DatasetRegistryRepository;
import com.qianxun.repo.IntentScenarioRepository;
import com.qianxun.repo.ModelRegistryRepository;
import com.qianxun.repo.SuggestedQuestionRepository;
import com.qianxun.repo.ToolDisplayNameRepository;
import com.qianxun.repo.UiConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
public class TiDBSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TiDBSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final QianxunProperties properties;
    private final IntentScenarioRepository intentScenarioRepository;
    private final DataFileRepository dataFileRepository;
    private final DataPortraitRepository dataPortraitRepository;
    private final AgentRegistryRepository agentRegistryRepository;
    private final ModelRegistryRepository modelRegistryRepository;
    private final DatasetRegistryRepository datasetRegistryRepository;
    private final UiConfigRepository uiConfigRepository;
    private final SuggestedQuestionRepository suggestedQuestionRepository;
    private final ToolDisplayNameRepository toolDisplayNameRepository;

    public TiDBSchemaInitializer(
            @Qualifier("tidbJdbcTemplate") JdbcTemplate jdbcTemplate,
            QianxunProperties properties,
            IntentScenarioRepository intentScenarioRepository,
            DataFileRepository dataFileRepository,
            DataPortraitRepository dataPortraitRepository,
            AgentRegistryRepository agentRegistryRepository,
            ModelRegistryRepository modelRegistryRepository,
            DatasetRegistryRepository datasetRegistryRepository,
            UiConfigRepository uiConfigRepository,
            SuggestedQuestionRepository suggestedQuestionRepository,
            ToolDisplayNameRepository toolDisplayNameRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.intentScenarioRepository = intentScenarioRepository;
        this.dataFileRepository = dataFileRepository;
        this.dataPortraitRepository = dataPortraitRepository;
        this.agentRegistryRepository = agentRegistryRepository;
        this.modelRegistryRepository = modelRegistryRepository;
        this.datasetRegistryRepository = datasetRegistryRepository;
        this.uiConfigRepository = uiConfigRepository;
        this.suggestedQuestionRepository = suggestedQuestionRepository;
        this.toolDisplayNameRepository = toolDisplayNameRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        int maxAttempts = 5;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                initSchema();
                return;
            } catch (Exception ex) {
                if (attempt < maxAttempts) {
                    log.warn("TiDB 初始化失败（第 {}/{} 次），10s 后重试: {}", attempt, maxAttempts, ex.getMessage());
                    try { Thread.sleep(10_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else {
                    log.error("TiDB 初始化在 {} 次重试后仍失败，应用将退出", maxAttempts);
                    throw new IllegalStateException("TiDB schema 初始化失败", ex);
                }
            }
        }
    }

    private void initSchema() {
        String db = properties.getDb();
        log.info("初始化 TiDB 库表: {}", db);
        jdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS `" + db + "`");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`chat_session` (
                    `id`         VARCHAR(64)   NOT NULL,
                    `user_id`    VARCHAR(64)   NOT NULL DEFAULT "1",
                    `title`      VARCHAR(1024) NOT NULL,
                    `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    `updated_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY `uk_id` (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """.formatted(db));

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`chat_message` (
                    `id`            VARCHAR(64)  NOT NULL,
                    `session_id`    VARCHAR(64)  NOT NULL,
                    `role`          VARCHAR(32)  NOT NULL,
                    `content`       TEXT         NOT NULL,
                    `thinking_mode` VARCHAR(16)  DEFAULT NULL,
                    `think_content` TEXT         DEFAULT NULL,
                    `entity_cards`  TEXT         DEFAULT NULL,
                    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY `uk_id` (`id`),
                    KEY `idx_session_id` (`session_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """.formatted(db));

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`intent_scenario` (
                    `id`               VARCHAR(64)  NOT NULL,
                    `code`             VARCHAR(128) NOT NULL,
                    `name`             VARCHAR(256) NOT NULL,
                    `description`      TEXT,
                    `examples`         TEXT,
                    `slot_schema`      TEXT,
                    `agent_skill`      VARCHAR(256) DEFAULT NULL,
                    `prompt_template`  TEXT,
                    `extra_params`     TEXT,
                    `priority`         INT          NOT NULL DEFAULT 100,
                    `enabled`          TINYINT      NOT NULL DEFAULT 1,
                    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY `uk_id` (`id`),
                    UNIQUE KEY `uk_code` (`code`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """.formatted(db));

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`chat_activity_log` (
                    `id`                   VARCHAR(64)  NOT NULL,
                    `user_id`              VARCHAR(64)  NOT NULL DEFAULT "1",
                    `session_id`           VARCHAR(64)  NOT NULL,
                    `user_message_id`      VARCHAR(64)  DEFAULT NULL,
                    `assistant_message_id` VARCHAR(64)  DEFAULT NULL,
                    `user_content`         TEXT         NOT NULL,
                    `nlu_intent`           VARCHAR(256) DEFAULT NULL,
                    `nlu_scenario_code`    VARCHAR(128) DEFAULT NULL,
                    `nlu_scenario_name`    VARCHAR(256) DEFAULT NULL,
                    `nlu_agent_skill`      VARCHAR(256) DEFAULT NULL,
                    `nlu_confidence`       DOUBLE       DEFAULT NULL,
                    `nlu_slots`            TEXT,
                    `nlu_missing_slots`    TEXT,
                    `nlu_reasoning`        TEXT,
                    `nlu_raw_response`     TEXT,
                    `llm_endpoint`         VARCHAR(512) DEFAULT NULL,
                    `llm_model`            VARCHAR(128) DEFAULT NULL,
                    `llm_request_json`     TEXT,
                    `llm_response_text`    TEXT,
                    `status`               VARCHAR(32)  NOT NULL DEFAULT 'success',
                    `error_message`        TEXT,
                    `thinking_mode`        VARCHAR(16)  DEFAULT NULL,
                    `think_content`        TEXT,
                    `nlu_duration_ms`      BIGINT       DEFAULT NULL,
                    `llm_duration_ms`      BIGINT       DEFAULT NULL,
                    `total_duration_ms`    BIGINT       DEFAULT NULL,
                    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY `uk_id` (`id`),
                    KEY `idx_user_id` (`user_id`),
                    KEY `idx_session_id` (`session_id`),
                    KEY `idx_created_at` (`created_at`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """.formatted(db));

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`message_feedback` (
                    `id`              VARCHAR(64)  NOT NULL,
                    `user_id`         VARCHAR(64)  NOT NULL DEFAULT "1",
                    `session_id`      VARCHAR(64)  NOT NULL,
                    `message_id`      VARCHAR(64)  NOT NULL,
                    `activity_log_id` VARCHAR(64)  DEFAULT NULL,
                    `feedback_type`   VARCHAR(16)  NOT NULL,
                    `feedback_note`   TEXT,
                    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY `uk_id` (`id`),
                    KEY `idx_user_id` (`user_id`),
                    KEY `idx_message_id` (`message_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """.formatted(db));

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`data_file` (
                    `id`           VARCHAR(64)  NOT NULL,
                    `name`         VARCHAR(512) NOT NULL,
                    `display_date` VARCHAR(32)  NOT NULL,
                    `kind`         VARCHAR(16)  NOT NULL,
                    `detail_text`  TEXT         DEFAULT NULL,
                    `detail_json`  LONGTEXT     DEFAULT NULL,
                    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY `uk_id` (`id`),
                    KEY `idx_display_date` (`display_date`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """.formatted(db));

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`data_portrait_point` (
                    `id`          VARCHAR(64)  NOT NULL,
                    `group_code`  VARCHAR(64)  NOT NULL DEFAULT 'default',
                    `unit`        VARCHAR(32)  NOT NULL DEFAULT '',
                    `order_index` INT          NOT NULL DEFAULT 0,
                    `label`       VARCHAR(64)  NOT NULL,
                    `series_a`    INT          NOT NULL DEFAULT 0,
                    `series_b`    INT          NOT NULL DEFAULT 0,
                    `focused`     TINYINT      NOT NULL DEFAULT 0,
                    `focus_label` VARCHAR(64)  DEFAULT NULL,
                    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY `uk_id` (`id`),
                    KEY `idx_group_order` (`group_code`,`order_index`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """.formatted(db));

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`agent_registry` (
                    `id`              VARCHAR(64)  NOT NULL,
                    `code`            VARCHAR(128) NOT NULL,
                    `name`            VARCHAR(256) NOT NULL,
                    `category`        VARCHAR(64)  NOT NULL DEFAULT 'general',
                    `description`     TEXT,
                    `icon`            VARCHAR(128) DEFAULT NULL,
                    `model_code`      VARCHAR(128) DEFAULT NULL,
                    `prompt_template` TEXT,
                    `priority`        INT          NOT NULL DEFAULT 100,
                    `enabled`         TINYINT      NOT NULL DEFAULT 1,
                    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY `uk_id` (`id`),
                    UNIQUE KEY `uk_code` (`code`),
                    KEY `idx_enabled_priority` (`enabled`,`priority`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """.formatted(db));

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`model_registry` (
                    `id`             VARCHAR(64)  NOT NULL,
                    `code`           VARCHAR(128) NOT NULL,
                    `name`           VARCHAR(256) NOT NULL,
                    `provider`       VARCHAR(64)  NOT NULL,
                    `base_url`       VARCHAR(512) DEFAULT NULL,
                    `context_window` INT          NOT NULL DEFAULT 128000,
                    `max_tokens`     INT          NOT NULL DEFAULT 16384,
                    `enabled`        TINYINT      NOT NULL DEFAULT 1,
                    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY `uk_id` (`id`),
                    UNIQUE KEY `uk_code` (`code`),
                    KEY `idx_enabled_updated` (`enabled`,`updated_at`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """.formatted(db));

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`dataset_registry` (
                    `id`          VARCHAR(64)  NOT NULL,
                    `code`        VARCHAR(128) NOT NULL,
                    `name`        VARCHAR(256) NOT NULL,
                    `description` TEXT,
                    `source_type` VARCHAR(64)  NOT NULL DEFAULT 'mixed',
                    `source_ref`  VARCHAR(512) DEFAULT NULL,
                    `doc_count`   INT          NOT NULL DEFAULT 0,
                    `enabled`     TINYINT      NOT NULL DEFAULT 1,
                    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY `uk_id` (`id`),
                    UNIQUE KEY `uk_code` (`code`),
                    KEY `idx_enabled_updated` (`enabled`,`updated_at`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """.formatted(db));

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`ui_string_config` (
                    `config_key`   VARCHAR(128) NOT NULL,
                    `config_value` TEXT         NOT NULL,
                    PRIMARY KEY (`config_key`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """.formatted(db));

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`suggested_question` (
                    `id`          VARCHAR(64)  NOT NULL,
                    `text`        TEXT         NOT NULL,
                    `category`    VARCHAR(64)  NOT NULL DEFAULT '',
                    `sort_order`  INT          NOT NULL DEFAULT 0,
                    `enabled`     TINYINT      NOT NULL DEFAULT 1,
                    PRIMARY KEY (`id`),
                    KEY `idx_enabled_sort` (`enabled`,`sort_order`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """.formatted(db));

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`tool_display_name` (
                    `tool_code`    VARCHAR(128) NOT NULL,
                    `display_name` VARCHAR(256) NOT NULL,
                    `sort_order`   INT            NOT NULL DEFAULT 0,
                    PRIMARY KEY (`tool_code`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """.formatted(db));

        log.info("TiDB 库表就绪");

        tryAlterAddColumn(db, "chat_session",      "user_id",       "VARCHAR(64) NOT NULL DEFAULT \"1\"");
        tryAlterAddColumn(db, "chat_activity_log", "user_id",       "VARCHAR(64) NOT NULL DEFAULT \"1\"");
        tryAlterAddColumn(db, "message_feedback",  "user_id",       "VARCHAR(64) NOT NULL DEFAULT \"1\"");
        tryAlterAddColumn(db, "chat_message",      "thinking_mode", "VARCHAR(16)");
        tryAlterAddColumn(db, "chat_message",      "think_content", "TEXT");
        tryAlterAddColumn(db, "chat_message",      "entity_cards",  "TEXT");
        tryAlterAddColumn(db, "chat_message",      "intent_analysis", "TEXT");
        tryAlterAddColumn(db, "chat_activity_log", "thinking_mode", "VARCHAR(16)");
        tryAlterAddColumn(db, "chat_activity_log", "think_content", "TEXT");
        tryAlterAddColumn(db, "data_file",         "detail_text",   "TEXT");
        tryAlterAddColumn(db, "data_file",         "detail_json",   "LONGTEXT");

        seedUiWelcomeAndToolsIfEmpty();
        seedDefaultScenariosIfEmpty();
        seedDataFilesIfEmpty();
        seedDataPortraitIfEmpty();
        seedModelRegistryIfEmpty();
        seedDatasetRegistryIfEmpty();
        seedAgentRegistryIfEmpty();
    }

    private void seedUiWelcomeAndToolsIfEmpty() {
        if (uiConfigRepository.count() == 0) {
            uiConfigRepository.upsert("welcome.disclaimer", "内容由 AI 大模型生成，请仔细甄别");
            uiConfigRepository.upsert("welcome.greeting", "你好，我是千寻问答助手");
            uiConfigRepository.upsert(
                    "welcome.capability",
                    "千寻能够学习你的语言，理解你的提问，进行多轮对话，帮助你高效获取信息、知识和灵感～"
            );
            uiConfigRepository.upsert("welcome.recommend_label", "你可以这样问我：");
            uiConfigRepository.upsert("portrait.series_a_label", "数据A");
            uiConfigRepository.upsert("portrait.series_b_label", "数据B");
            log.info("ui_string_config 种子数据已写入");
        }
        if (suggestedQuestionRepository.count() == 0) {
            suggestedQuestionRepository.insert(new SuggestedQuestion(
                    newId(),
                    "请检索过去24小时与“低空经济”相关的重点政策动态，并按地区汇总。",
                    "intel",
                    10,
                    true
            ));
            suggestedQuestionRepository.insert(new SuggestedQuestion(
                    newId(),
                    "帮我梳理本周“人工智能芯片”领域的重要新闻，标注来源与发布时间。",
                    "intel",
                    20,
                    true
            ));
            suggestedQuestionRepository.insert(new SuggestedQuestion(
                    newId(),
                    "请查询“跨境电商”近7天舆情热点，给出风险点和机会点。",
                    "intel",
                    30,
                    true
            ));
            log.info("suggested_question 种子数据已写入");
        }
        if (toolDisplayNameRepository.count() == 0) {
            toolDisplayNameRepository.insert(new ToolDisplayName("web_search", "网页搜索", 10));
            toolDisplayNameRepository.insert(new ToolDisplayName("execute_code", "执行代码", 20));
            toolDisplayNameRepository.insert(new ToolDisplayName("web_extract", "网页内容获取", 30));
            log.info("tool_display_name 种子数据已写入");
        }
    }

    private void seedDataFilesIfEmpty() {
        if (dataFileRepository.count() > 0) {
            return;
        }
        Instant now = Instant.now();
        String[][] seeds = {
                {"XXXX人员情报...docx", "2026-08-12", DataFile.KIND_WORD},
                {"XXXX人员情报...xls",  "2026-08-12", DataFile.KIND_EXCEL},
                {"XXXX人员情报...docx", "2026-08-12", DataFile.KIND_WORD},
                {"XXXX人员情报...xls",  "2026-08-12", DataFile.KIND_EXCEL},
                {"XXXX人员情报...docx", "2026-08-12", DataFile.KIND_WORD},
                {"XXXX人员情报...xls",  "2026-08-12", DataFile.KIND_EXCEL},
                {"XXXX人员情报...docx", "2026-08-12", DataFile.KIND_WORD},
                {"XXXX人员情报...xls",  "2026-08-12", DataFile.KIND_EXCEL},
                {"XXXX人员情报...docx", "2026-08-12", DataFile.KIND_WORD},
                {"XXXX人员情报...xls",  "2026-08-12", DataFile.KIND_EXCEL},
                {"XXXX人员情报...docx", "2026-08-12", DataFile.KIND_WORD},
                {"XXXX人员情报...xls",  "2026-08-12", DataFile.KIND_EXCEL},
        };
        for (String[] seed : seeds) {
            try {
                String detailText = """
                        %s 详细内容预览：
                        1. 来源：系统导入
                        2. 时间：%s
                        3. 摘要：该文档包含与情报检索研判相关的关键线索，请结合上下文甄别。
                        """.formatted(seed[0], seed[1]);
                String detailJson = """
                        [["字段","值"],["文件名","%s"],["日期","%s"],["类型","%s"],["状态","可用"]]
                        """.formatted(seed[0], seed[1], seed[2]);
                dataFileRepository.insert(new DataFile(newId(), seed[0], seed[1], seed[2], detailText, detailJson, now, now));
            } catch (Exception ex) {
                log.warn("data_file 种子写入失败: {}", ex.toString());
            }
        }
        log.info("data_file 种子数据已写入: {} 条", seeds.length);
    }

    private void seedDataPortraitIfEmpty() {
        String group = DataPortraitPoint.DEFAULT_GROUP;
        if (dataPortraitRepository.countByGroup(group) > 0) {
            return;
        }
        String unit = "个";
        String[] labels  = {"1月", "2月", "3月", "4月", "5月", "6月"};
        int[]    series_a = {8, 12, 14, 19, 16, 14};
        int[]    series_b = {6, 9, 11, 12, 13, 11};
        int focusIndex   = 3;
        String focusLabel = "2021.01.06";
        for (int i = 0; i < labels.length; i++) {
            try {
                dataPortraitRepository.insert(new DataPortraitPoint(
                        newId(), group, unit, i, labels[i],
                        series_a[i], series_b[i], i == focusIndex, focusLabel
                ));
            } catch (Exception ex) {
                log.warn("data_portrait 种子写入失败: {}", ex.toString());
            }
        }
        log.info("data_portrait 种子数据已写入: {} 条", labels.length);
    }

    private void seedModelRegistryIfEmpty() {
        if (modelRegistryRepository.count() > 0) {
            return;
        }
        Instant now = Instant.now();
        List<ModelRegistryItem> seeds = List.of(
                new ModelRegistryItem(newId(), "moonshot-k2", "Moonshot K2 Turbo", "kimi-coding",
                        "https://api.moonshot.cn/v1", 262144, 16384, true, now, now),
                new ModelRegistryItem(newId(), "qianxun-default", "千寻默认模型", "openai-compatible",
                        "http://hermes-agent:8642/v1", 128000, 16384, true, now, now)
        );
        for (ModelRegistryItem item : seeds) {
            try {
                modelRegistryRepository.insert(item);
            } catch (Exception ex) {
                log.warn("model_registry 种子写入失败: {}", ex.toString());
            }
        }
        log.info("model_registry 种子数据已写入: {} 条", seeds.size());
    }

    private void seedAgentRegistryIfEmpty() {
        if (agentRegistryRepository.count() > 0) {
            return;
        }
        Instant now = Instant.now();
        List<AgentRegistryItem> seeds = List.of(
                new AgentRegistryItem(newId(), "qianxun-main", "千寻", "assistant",
                        "主对话智能体，负责通用问答与情报检索研判。", "bot", "moonshot-k2", "", 10, true, now, now),
                new AgentRegistryItem(newId(), "zhiguan", "智观", "analysis",
                        "侧重数据趋势与画像分析。", "compass", "moonshot-k2", "", 20, true, now, now),
                new AgentRegistryItem(newId(), "market-agent", "智能体超市", "market",
                        "可扩展的行业智能体目录。", "store", "moonshot-k2", "", 30, true, now, now)
        );
        for (AgentRegistryItem item : seeds) {
            try {
                agentRegistryRepository.insert(item);
            } catch (Exception ex) {
                log.warn("agent_registry 种子写入失败: {}", ex.toString());
            }
        }
        log.info("agent_registry 种子数据已写入: {} 条", seeds.size());
    }

    private void seedDatasetRegistryIfEmpty() {
        if (datasetRegistryRepository.count() > 0) {
            return;
        }
        Instant now = Instant.now();
        List<DatasetRegistryItem> seeds = List.of(
                new DatasetRegistryItem(newId(), "qianxun-default-dataset", "千寻默认数据集",
                        "默认知识数据集，覆盖中间数据与画像样例。", "tidb", "qianxun.data_file,data_portrait_point", 300, true, now, now),
                new DatasetRegistryItem(newId(), "intel-mail-archive", "邮件情报归档库",
                        "邮件类情报样本归档数据集。", "mail", "mail://archive/intel", 128, true, now, now)
        );
        for (DatasetRegistryItem item : seeds) {
            try {
                datasetRegistryRepository.insert(item);
            } catch (Exception ex) {
                log.warn("dataset_registry 种子写入失败: {}", ex.toString());
            }
        }
        log.info("dataset_registry 种子数据已写入: {} 条", seeds.size());
    }

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
        Instant now = Instant.now();
        List<IntentScenario> seeds = List.of(
                // buildOrgResearch(now),
                // buildPersonResearch(now),
                // buildGroupResearch(now),
                // buildEventAnalysis(now),
                buildIntelligenceRetrieval(now),
                buildGeneral(now)
        );
        for (IntentScenario s : seeds) {
            try {
                var existing = intentScenarioRepository.findByCode(s.code());
                if (existing.isPresent()) {
                    IntentScenario updated = new IntentScenario(
                            existing.get().id(),
                            s.code(), s.name(), s.description(),
                            s.examples(), s.slots(), s.agentSkill(),
                            s.promptTemplate(), s.extraParams(),
                            s.priority(), s.enabled(),
                            existing.get().createdAt(), now
                    );
                    intentScenarioRepository.updateAll(updated);
                    log.info("已更新意图场景种子: code={} name={}", s.code(), s.name());
                } else {
                    intentScenarioRepository.insert(s);
                    log.info("已写入意图场景种子: code={} name={}", s.code(), s.name());
                }
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
                "对境外政府机构、军事组织、情报部门、智库、战略企业及境外非政府组织进行多维度情报评估，支撑国家安全决策。",
                List.of(
                        "调研一下美国中央情报局CIA的组织架构和近期动向",
                        "分析英国军情六处MI6的人员规模和核心业务",
                        "查看澳大利亚战略政策研究所ASPI的背景和资金来源",
                        "了解一下俄罗斯联邦安全局FSB的最新改革情况",
                        "调研欧洲对外行动署EAAS的组织架构"
                ),
                List.of(
                        new SlotDefinition("org_name", "string", true,
                                "目标机构名称（英文全称或常用简称，如 CIA、MI6、FSB）", List.of()),
                        new SlotDefinition("org_country", "string", false,
                                "机构所属国家或地区，用于同名消歧，如「美国」「英国」「俄罗斯」", List.of()),
                        new SlotDefinition("focus", "enum", false, "调研聚焦维度，默认 all",
                                List.of("overview", "leadership", "capability", "activity",
                                        "finance", "relationship", "threat_assessment", "all")),
                        new SlotDefinition("time_range", "string", false,
                                "关注时间范围，如「近半年」「2024年至今」", List.of())
                ),
                "org_research_agent",
                """
                        你现在扮演「机构情报分析专家」，请基于已识别的结构化槽位完成多维度情报评估报告。
                        - 机构名称：{{org_name}}
                        - 所属国家：{{org_country|未指定}}
                        - 关注维度：{{focus|all}}
                        - 时间范围：{{time_range|不限}}

                        请按以下框架系统输出（信息缺失标注「未公开」或「待补充」，切勿编造）：

                        1. 机构概况
                           - 机构全称、简称、官方定位与法定职能
                           - 成立时间、历史沿革与历次重大改革
                           - 组织架构（内设部门、下属单位、层级关系）
                           - 总部及主要驻地的地理位置
                           - 公开预算规模与人员规模估算

                        2. 领导层分析
                           - 现任主要负责人姓名、任期、履历背景
                           - 历任领导（近3任）及交接时间
                           - 领导层政治立场、决策风格与对华态度
                           - 关键部门负责人及其专业背景

                        3. 核心能力评估
                           - 主要业务方向（情报搜集/分析/行动/反情报等）
                           - 技术手段（人力情报HUMINT、技术情报TECHINT、信号情报SIGINT等）
                           - 海外基地、站点或合作网络分布
                           - 情报共享机制（与盟友机构的信息交换协议）

                        4. 近期动态与活动
                           - 近 {{time_range|半年}} 内重大活动、声明、人事变动
                           - 针对中国的公开情报行动记录（如有）
                           - 参与的国际多边安全机制或联盟

                        5. 资金来源与运营模式
                           - 公开预算来源与分配结构
                           - 与私营企业、智库、学术机构的资金往来
                           - 灰色或非公开资金渠道（如可查）

                        6. 关系网络分析
                           - 与本国其他安全/军事/外交机构的协调机制
                           - 与盟友国家对应机构的关系深度
                           - 与非政府组织、媒体、智库的关联

                        7. 对华威胁评估
                           - 该机构当前对华战略定位（对手/监控对象/合作方）
                           - 已知的对华情报行动类型与频率
                           - 对中国国家安全构成的具体威胁等级（高/中/低）
                           - 历史上有据可查的对华敌对行动

                        8. 后续追踪建议
                           - 最值得持续关注的 5 个动态指标
                           - 建议优先深入调查的方向或疑点
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
                "对特定人物进行全方位安全画像：身份背景、社会关系、出行轨迹、涉华活动及风险评估，" +
                "支撑防范渗透、识别代理人和发现利益代言网络等情报任务。",
                List.of(
                        "调研一下澳大利亚战略政策研究所ASPI的研究员孙某某的背景",
                        "查一下英国广播公司BBC驻华记者托尼·海恩的详细信息",
                        "分析一下美国国家民主基金会NED资金扶持的那些人",
                        "调研一下香港外国记者会FCC主要负责人的背景",
                        "帮我查下这个人的海外关系和资金来源"
                ),
                List.of(
                        new SlotDefinition("person_name", "string", true,
                                "目标人物姓名（中文或英文均可）", List.of()),
                        new SlotDefinition("affiliation", "string", false,
                                "所属机构/单位/国籍，用于同名消歧，如「ASPI」「BBC」「美国」", List.of()),
                        new SlotDefinition("focus", "enum", false,
                                "调研聚焦维度，默认 all",
                                List.of("identity", "travel", "finance", "relationship",
                                        "media_activity", "china_involvement", "risk_assessment", "all")),
                        new SlotDefinition("nationality", "string", false,
                                "国籍或护照签发国，如「英国」「澳大利亚」「美国」", List.of()),
                        new SlotDefinition("time_range", "string", false,
                                "动态关注的时间范围，如「近一年」「2023-2024」", List.of())
                ),
                "person_research_agent",
                """
                        你现在扮演「人物情报分析专家」，请围绕目标人物输出系统性安全画像报告。
                        - 目标人物：{{person_name}}
                        - 所属机构/国籍：{{affiliation|未指定}}
                        - 调研维度：{{focus|all}}
                        - 时间范围：{{time_range|不限}}

                        请按以下框架系统输出（信息缺失标注「未公开」或「待核实」，切勿编造；
                        所有分析均基于公开信息来源，仅作情报参考）：

                        1. 身份与背景核实
                           - 全名（中英文名）、曾用名/别名、出生年月
                           - 国籍与持有护照类型（是否有多国护照）
                           - 出生地、成长地、现居住地
                           - 民族、宗教信仰（如可查）
                           - 学术背景（学历、学校、导师、同届重要同学）

                        2. 职业与社会履历
                           - 当前职务、所属机构及在组织架构中的位置
                           - 历任职位时间线（含机构名称、职级、核心工作内容）
                           - 与本国安全/情报/军事部门的明确关联或间接关联
                           - 参与的国际组织、多边机制或网络

                        3. 出入境轨迹分析
                           - 近 {{time_range|两年}} 内进出中国内地及港澳台的记录特征（频率、目的）
                           - 主要出访目的地及频次（可推断重点关注区域）
                           - 是否持有中国签证及签证类型（如可查）
                           - 出入境行为异常点（频繁短期入境、敏感地区经停等）

                        4. 资金与财务脉络
                           - 受雇机构性质（政府/军方/智库/媒体/非政府组织）
                           - 已知薪酬来源及金额区间
                           - 是否接受境外政府资金、项目资助或专项拨款
                           - 与各类基金会的资金往来（如索罗斯基金会、福特基金会、NED等）
                           - 是否有在华商业利益或被中国机构聘请

                        5. 关系网络追踪
                           - 密切接触者（同事、合作者）名单及背景
                           - 与中国官方或学术机构的联系记录
                           - 家庭成员中是否有中国国籍或在华活动
                           - 在华服务或受雇于中国机构的记录
                           - 关键关系人的身份与影响力评估

                        6. 涉华活动与立场分析
                           - 近年来公开发表的涉华言论、文章、报告（按时间倒序）
                           - 对华政策立场（友好/中立/敌对）及转变节点
                           - 是否参与发起针对中国的倡议、联署、请愿
                           - 在涉疆、涉藏、涉港、涉台等敏感议题上的立场
                           - 与中国官方互动的正式记录（采访、访问、论坛参与等）

                        7. 媒体与信息战角色评估
                           - 在社交媒体（Twitter/X、LinkedIn等）的活跃度与影响力（粉丝数、发言频率）
                           - 是否为所谓"中国研究"领域的活跃写手或意见领袖
                           - 是否参与制作或传播对中国形象不利的报道/内容
                           - 被中国官方机构点名、制裁或限制入境的记录（如有）

                        8. 综合风险评估
                           - 对中国国家安全的威胁等级（高 / 中 / 低）
                           - 威胁类型判断：政治渗透 / 情报搜集 / 信息战 / 代理人活动 / 其他
                           - 当前活跃度评估（是否处于高危行动期）
                           - 建议列入管控/关注名单的依据

                        9. 后续追踪建议
                           - 最值得持续关注的 5 个行为指标
                           - 建议深入调查的方向与线索
                        """,
                Map.of(),
                190,
                true,
                now,
                now
        );
    }

    private static IntentScenario buildGroupResearch(Instant now) {
        return new IntentScenario(
                newId(),
                "group_research",
                "群体调研",
                "对特定目标群体（境外势力机构、媒体网络、代理人网络、社会运动组织、极端组织等）" +
                "进行结构化情报分析，识别其组织形态、行动模式、资金链条及对华威胁程度。",
                List.of(
                        "分析一下境外反华媒体网络的组织架构和资金来源",
                        "调研海外民运组织的派系分布和资金渠道",
                        "研究东突、藏独、港独等分裂势力在海外的活动网络",
                        "分析境外非政府组织在华渗透的行动模式",
                        "调研台独势力在海外的游说团体和资金支持"
                ),
                List.of(
                        new SlotDefinition("group_name", "string", true,
                                "目标群体名称（组织名称或类别描述）", List.of()),
                        new SlotDefinition("group_type", "enum", false,
                                "群体类型，用于初步定性",
                                List.of("anti_china_media", "foreign_ngo", "secession_group",
                                        "political_opposition", "extremist_group", "foreign_agency", "all")),
                        new SlotDefinition("country", "string", false,
                                "主要活动所在国家或地区，如「美国」「澳大利亚」「香港」", List.of()),
                        new SlotDefinition("focus", "enum", false,
                                "调研重点维度，默认 all",
                                List.of("overview", "structure", "finance", "activity",
                                        "china_impact", "countermeasures", "all")),
                        new SlotDefinition("time_range", "string", false,
                                "关注时间范围，如「近三年」「2022年至今」", List.of())
                ),
                "group_research_agent",
                """
                        你现在扮演「群体情报分析专家」，请围绕目标群体完成系统性情报评估报告。
                        - 目标群体：{{group_name}}
                        - 群体类型：{{group_type|all}}
                        - 主要所在国家：{{country|未指定}}
                        - 调研维度：{{focus|all}}
                        - 时间范围：{{time_range|不限}}

                        请按以下框架系统输出（信息缺失标注「未公开」或「待补充」，切勿编造）：

                        1. 群体概况
                           - 正式名称、简称、内部代号（如有）
                           - 成立时间、历史沿革与成立背景（创始动机）
                           - 官方使命宣言与实际活动宗旨对比
                           - 组织规模估算（成员数量、层级结构、核心圈子）

                        2. 组织架构分析
                           - 核心决策层人员名单及背景（至少3-5名关键人物）
                           - 内部分工体系（部门/委员会/专项小组）
                           - 与上级机构、资助方、合作组织的纵向关系
                           - 横向联盟：与其他同类组织的联合行动记录

                        3. 资金来源追踪
                           - 已知的公开资助方（政府拨款、基金会捐款、企业资助）
                           - 资金规模估算及主要流向（行政/行动/宣传）
                           - 是否涉及本国政府直接拨款（以项目合作等名义）
                           - 隐蔽资金渠道（如多层嵌套的壳公司、现金转账等）
                           - 资金异常的预警信号

                        4. 行动模式分析
                           - 主要活动类型（游说、媒体宣传、社会运动、法律行动等）
                           - 近 {{time_range|三年}} 内的重大行动时间线
                           - 行动策略特征（公开/隐蔽、暴力/非暴力、渐进/激进）
                           - 是否在中国境内存在分支、代理人或线下活动
                           - 数字空间行动（社媒传播、虚假信息行动）

                        5. 对华影响与威胁评估
                           - 该群体对中国的核心敌对行动或政策主张
                           - 是否参与制定或推动反华法案、制裁措施
                           - 在涉疆、涉藏、涉港、涉台、南海等议题上的立场与行动
                           - 对中国国家形象、社会稳定的具体损害评估
                           - 与台湾当局、达赖集团、"民运"组织等的关系深度

                        6. 可用对策建议
                           - 情报监控：建议重点跟进的指标（资金流、关键人员活动、联盟扩展）
                           - 法律工具：可援引的法律条款或制裁手段
                           - 舆论反制：正面发声策略建议
                           - 渠道管控：媒体/平台层面的限制或引导建议

                        7. 后续追踪建议
                           - 最值得持续关注的 5 个动态指标
                           - 建议深入调查的优先方向
                        """,
                Map.of(),
                195,
                true,
                now,
                now
        );
    }

    private static IntentScenario buildEventAnalysis(Instant now) {
        return new IntentScenario(
                newId(),
                "event_analysis",
                "事件分析",
                "对重大国际事件、地缘政治变化、安全突发事件进行多维度深度研判：" +
                "背景溯源、时间线梳理、成因剖析、对华影响评估、各方战略意图及对我国家安全的长远启示。",
                List.of(
                        "分析一下美军在南海的侦察行动升级事件",
                        "梳理近期外国议会通过的涉华法案及其影响",
                        "分析五眼联盟近期针对中国的情报合作动态",
                        "解读欧盟对中国企业的制裁措施及背后推力",
                        "帮我梳理一下台海局势近期的重大事件脉络",
                        "分析境外势力在新疆人权议题上的炒作事件"
                ),
                List.of(
                        new SlotDefinition("event_name", "string", true,
                                "事件名称或核心描述，如「美军舰过航台海」「某国议会通过涉疆决议」", List.of()),
                        new SlotDefinition("event_time", "string", false,
                                "事件发生时间或时间段，如「2024年3月」「近半年」", List.of()),
                        new SlotDefinition("event_location", "string", false,
                                "事件发生地点或涉及地区，如「南海」「台湾海峡」「日内瓦」", List.of()),
                        new SlotDefinition("involved_parties", "string", false,
                                "关键涉事主体（国家/机构/人物），多个用逗号分隔", List.of()),
                        new SlotDefinition("domain", "enum", false,
                                "事件所属领域，默认 auto 自动判断",
                                List.of("military", "diplomatic", "intelligence", "economic",
                                        "information_war", "lawmaking", "human_rights", "auto")),
                        new SlotDefinition("analysis_focus", "enum", false,
                                "分析侧重维度，默认 all",
                                List.of("background", "timeline", "china_impact", "stakeholder_intent",
                                        "international_reaction", "trend_outlook", "counter_recommend", "all")),
                        new SlotDefinition("time_range", "string", false,
                                "分析覆盖的时间跨度，如「事件发生后3个月」「近一年」", List.of())
                ),
                "event_analysis_agent",
                """
                        你现在扮演「国家安全事件研判专家」，请对目标事件进行系统性、多维度的深度分析。
                        - 事件：{{event_name}}
                        - 发生时间：{{event_time|不限}}
                        - 涉及地区：{{event_location|不限}}
                        - 关键涉事方：{{involved_parties|不限}}
                        - 事件领域：{{domain|auto}}
                        - 分析重点：{{analysis_focus|all}}
                        - 分析时间跨度：{{time_range|不限}}

                        请按以下框架系统输出（信息缺失标注「资料不足」，切勿编造）：

                        1. 事件概述
                           - 一段话核心摘要（What / When / Where / Who / Why）
                           - 事件性质与对我国家安全的重要性评级（高 / 中 / 低）

                        2. 背景与根源分析
                           - 宏观地缘政治背景（中美关系、区域安全格局等）
                           - 直接导火索与深层结构性原因
                           - 相关方的历史恩怨或利益纠葛

                        3. 事件时间线
                           - 按时间顺序列出关键节点与转折（格式：时间 → 事件节点）
                           - 标注各方关键应对动作

                        4. 对中国国家安全的影响评估
                           - 短期直接影响（军事安全 / 政治压力 / 经济利益 / 信息环境）
                           - 中长期战略影响（力量对比变化、盟友体系演变、话语权争夺）
                           - 我核心利益受损程度评估

                        5. 各方战略意图研判
                           - 每个涉事方在此事件中的核心诉求与利益计算
                           - 是否存在事先策划的成分（试探性行动 / 有组织施压）
                           - 与近期其他涉华事件的联动性分析
                           - 是否有转嫁国内矛盾或迎合选民的政治动机

                        6. 国际反应与联动
                           - 盟友国家的公开支持或呼应
                           - 国际组织的表态（联合国、欧盟、东盟等）
                           - 媒体和舆论场的配合炒作情况
                           - 是否形成对华遏制包围的协同行动

                        7. 趋势研判与后续发展
                           - 最可能的 3 种演变情景（强硬化 / 维持现状 / 缓和）
                           - 关键转折信号（哪些事件出现意味着事态升级或降温）
                           - 后续可能出现的连锁反应或模仿效应

                        8. 反制与应对建议
                           - 情报监控：建议重点跟进的指标
                           - 外交对冲：可采取的舆论、外交、法律组合反制
                           - 底线防控：必须守住的利益红线
                           - 值得向上级预警的核心判断

                        9. 后续追踪建议
                           - 未来 3 个月内最值得持续跟进的 5 个动态指标
                           - 建议深入调查的疑点或方向
                        """,
                Map.of(),
                185,
                true,
                now,
                now
        );
    }

    private static IntentScenario buildIntelligenceRetrieval(Instant now) {
        return new IntentScenario(
                newId(),
                "intelligence_retrieval",
                "情报检索研判",
                "从海量邮件、语音、文档等情报数据中进行检索分析，提取高价值情报线索，支持国家安全研判决策。",
                List.of(
                        "检索涉密人员与境外机构的异常邮件往来",
                        "查找近期关于军事设施的敏感语音记录",
                        "分析某间谍嫌疑人的通信行为模式",
                        "检索境外势力在华情报活动的蛛丝马迹",
                        "查找涉及核心技术泄露的可疑通信",
                        "分析某可疑人物的社交网络关系",
                        "检索外国情报机构在华活动线索",
                        "查找涉及国家安全的跨境资金流向"
                ),
                List.of(
                        new SlotDefinition("retrieval_target", "string", true,
                                "检索目标描述，如「涉密人员邮件」「可疑语音」「某嫌疑人通信」", List.of()),
                        new SlotDefinition("data_source", "enum", false,
                                "数据来源类型",
                                List.of("email", "voice", "document", "chat", "financial", "travel", "all")),
                        new SlotDefinition("suspect_name", "string", false,
                                "嫌疑人或关注对象姓名（如有）", List.of()),
                        new SlotDefinition("suspect_organization", "string", false,
                                "嫌疑人所属机构或关联组织", List.of()),
                        new SlotDefinition("keywords", "string", false,
                                "关键词或短语，多个用逗号分隔", List.of()),
                        new SlotDefinition("time_range", "string", false,
                                "检索时间范围，如「近一个月」「2024年1月至3月」", List.of()),
                        new SlotDefinition("country_region", "string", false,
                                "涉及的国家或地区", List.of()),
                        new SlotDefinition("threat_level", "enum", false,
                                "威胁等级筛选",
                                List.of("critical", "high", "medium", "low", "all")),
                        new SlotDefinition("analysis_focus", "enum", false,
                                "分析重点",
                                List.of("retrieval_result", "pattern_analysis", "network_relation",
                                        "timeline_reconstruction", "threat_assessment", "all")),
                        new SlotDefinition("output_format", "enum", false,
                                "输出格式偏好",
                                List.of("detailed_report", "summary", "list", "timeline", "all"))
                ),
                "intelligence_retrieval_agent",
                """
                        你现在扮演「国家安全情报分析师」，从海量情报数据中检索、分析、提炼高价值信息，完成情报研判报告。
                        - 检索目标：{{retrieval_target}}
                        - 数据来源：{{data_source|all}}
                        - 嫌疑人姓名：{{suspect_name|未指定}}
                        - 关联机构：{{suspect_organization|未指定}}
                        - 关键词：{{keywords|无}}
                        - 时间范围：{{time_range|不限}}
                        - 涉及国家/地区：{{country_region|不限}}
                        - 威胁等级：{{threat_level|all}}
                        - 分析重点：{{analysis_focus|all}}
                        - 输出格式：{{output_format|detailed_report}}

                        请按以下框架系统输出（信息缺失标注「未掌握」或「需进一步调查」，切勿编造）：

                        1. 情报检索摘要
                           - 检索范围概述（数据来源、时间跨度、关键词策略）
                           - 检索命中基本情况（命中数量级、高价值条目占比）
                           - 初步评估：是否发现值得关注的情报线索

                        2. 高价值情报线索（按威胁等级排序）
                           - 每条线索的来源数据类型、采集时间
                           - 关键内容摘要（脱敏处理）
                           - 情报价值评级（极高/高/中）及依据
                           - 与其他线索的关联性

                        3. 行为模式分析
                           - 目标人员/机构的通信频率和时间规律
                           - 通信对象网络图谱（核心节点、关键联系人）
                           - 异常行为识别（加密通信、夜间活动、特定地点聚集等）
                           - 与已知情报组织活动模式的对标分析

                        4. 时间线重构
                           - 关键事件时间线（格式：时间 → 事件/行为）
                           - 活动规律总结（周期性、季节性、事件驱动型）
                           - 关键转折点识别

                        5. 威胁评估与研判
                           - 对国家安全的具体威胁类型（情报搜集/技术窃取/渗透颠覆/干涉内政）
                           - 威胁可信度和紧迫性评估
                           - 可能的下一步行动预判
                           - 是否需要启动专项调查

                        6. 建议措施
                           - 情报监控：建议持续跟进的指标和节点
                           - 调查建议：需要进一步取证的优先方向
                           - 处置建议：是否建议执法介入或外交反制
                           - 信息共享：是否需要通报相关部门或上级

                        7. 证据链整理（如发现高价值线索）
                           - 主要证据摘要（脱敏后）
                           - 证据链完整度评估
                           - 建议的证据保全措施
                           - 后续调查路径建议

                        注意事项：
                        - 所有敏感信息需脱敏处理后再呈现
                        - 分析结论需有充分事实依据，区分「已证实」「高度可疑」「待核实」
                        - 标注信息的不确定性和可信度区间
                        - 必须要备注标注引用的邮件或语音的原始文件内容摘要和文件编号
                        """,
                Map.of(),
                205,
                true,
                now,
                now
        );
    }

    private static IntentScenario buildGeneral(Instant now) {
        return new IntentScenario(
                newId(),
                IntentScenario.GENERAL_CODE,
                "一般问答",
                "一般问答",
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