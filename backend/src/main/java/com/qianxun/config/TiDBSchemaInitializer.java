package com.qianxun.config;

import com.qianxun.domain.DataPortraitPoint;
import com.qianxun.domain.DatasetRegistryItem;
import com.qianxun.domain.ModelRegistryItem;
import com.qianxun.domain.SuggestedQuestion;
import com.qianxun.domain.ToolDisplayName;
import com.qianxun.domain.AppUser;
import com.qianxun.llm.ClaudeCodeToolCatalog;
import com.qianxun.repo.AgentRegistryRepository;
import com.qianxun.repo.AppUserRepository;
import com.qianxun.repo.DataPortraitRepository;
import com.qianxun.repo.DatasetRegistryRepository;
import com.qianxun.repo.ModelRegistryRepository;
import com.qianxun.repo.SuggestedQuestionRepository;
import com.qianxun.repo.ToolDisplayNameRepository;
import com.qianxun.repo.UiConfigRepository;
import com.qianxun.security.UserRoles;
import com.qianxun.service.ToolDisplayNames;
import com.qianxun.service.WelcomeOfficerPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final DataPortraitRepository dataPortraitRepository;
    private final AgentRegistryRepository agentRegistryRepository;
    private final ModelRegistryRepository modelRegistryRepository;
    private final DatasetRegistryRepository datasetRegistryRepository;
    private final UiConfigRepository uiConfigRepository;
    private final SuggestedQuestionRepository suggestedQuestionRepository;
    private final ToolDisplayNameRepository toolDisplayNameRepository;
    private final ToolDisplayNames toolDisplayNames;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public TiDBSchemaInitializer(
            @Qualifier("tidbJdbcTemplate") JdbcTemplate jdbcTemplate,
            QianxunProperties properties,
            DataPortraitRepository dataPortraitRepository,
            AgentRegistryRepository agentRegistryRepository,
            ModelRegistryRepository modelRegistryRepository,
            DatasetRegistryRepository datasetRegistryRepository,
            UiConfigRepository uiConfigRepository,
            SuggestedQuestionRepository suggestedQuestionRepository,
            ToolDisplayNameRepository toolDisplayNameRepository,
            ToolDisplayNames toolDisplayNames,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.dataPortraitRepository = dataPortraitRepository;
        this.agentRegistryRepository = agentRegistryRepository;
        this.modelRegistryRepository = modelRegistryRepository;
        this.datasetRegistryRepository = datasetRegistryRepository;
        this.uiConfigRepository = uiConfigRepository;
        this.suggestedQuestionRepository = suggestedQuestionRepository;
        this.toolDisplayNameRepository = toolDisplayNameRepository;
        this.toolDisplayNames = toolDisplayNames;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
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
        // 仅幂等建库建表 / 补列。启动时绝不 DROP DATABASE、DROP TABLE，也不清空业务表。
        jdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS `" + db + "`");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`app_user` (
                    `id`            VARCHAR(64)   NOT NULL,
                    `username`      VARCHAR(128)  NOT NULL,
                    `display_name`  VARCHAR(256)  NOT NULL DEFAULT "",
                    `password_hash` VARCHAR(255)  NOT NULL,
                    `role`          VARCHAR(32)   NOT NULL DEFAULT "functional",
                    `enabled`       TINYINT(1)    NOT NULL DEFAULT 1,
                    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY `uk_id` (`id`),
                    UNIQUE KEY `uk_username` (`username`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """.formatted(db));
        seedAdminUserIfMissing();

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`chat_session` (
                    `id`              VARCHAR(64)   NOT NULL,
                    `user_id`         VARCHAR(64)   NOT NULL DEFAULT "1",
                    `title`           VARCHAR(1024) NOT NULL,
                    `agent_code`      VARCHAR(128)  NOT NULL DEFAULT "",
                    `hermes_profile`  VARCHAR(128)  NOT NULL DEFAULT "",
                    `agent_name`      VARCHAR(256)  NOT NULL DEFAULT "",
                    `session_goal`    TEXT,
                    `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    `updated_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY `uk_id` (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """.formatted(db));

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`chat_message` (
                    `id`            VARCHAR(64)  NOT NULL,
                    `session_id`    VARCHAR(64)  NOT NULL,
                    `role`          VARCHAR(32)  NOT NULL,
                    `content`       LONGTEXT     NOT NULL,
                    `thinking_mode` VARCHAR(16)  DEFAULT NULL,
                    `think_content` LONGTEXT     DEFAULT NULL,
                    `entity_cards`  LONGTEXT     DEFAULT NULL,
                    `tool_calls`    LONGTEXT     DEFAULT NULL,
                    `usage_json`    TEXT         DEFAULT NULL,
                    `suggestions_json` TEXT      DEFAULT NULL,
                    `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    `status`        VARCHAR(32)  NOT NULL DEFAULT 'completed',
                    `run_id`        VARCHAR(64)  DEFAULT NULL,
                    UNIQUE KEY `uk_id` (`id`),
                    KEY `idx_session_id` (`session_id`)
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
                    `id`           VARCHAR(64)   NOT NULL,
                    `user_id`      VARCHAR(64)   NOT NULL DEFAULT "1",
                    `name`         VARCHAR(512)  NOT NULL,
                    `display_date` VARCHAR(32)   NOT NULL,
                    `kind`         VARCHAR(16)   NOT NULL,
                    `detail_text`  LONGTEXT      DEFAULT NULL,
                    `detail_json`  LONGTEXT      DEFAULT NULL,
                    `object_key`   VARCHAR(1024) DEFAULT NULL,
                    `content_type` VARCHAR(256)  DEFAULT NULL,
                    `size_bytes`   BIGINT        DEFAULT NULL,
                    `public_token` VARCHAR(64)   DEFAULT NULL,
                    `folder_path`  VARCHAR(512)  NOT NULL DEFAULT "",
                    `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY `uk_id` (`id`),
                    UNIQUE KEY `uk_public_token` (`public_token`),
                    KEY `idx_display_date` (`display_date`),
                    KEY `idx_user_id` (`user_id`),
                    KEY `idx_user_folder` (`user_id`, `folder_path`(191))
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
                    `welcome_title`   VARCHAR(512) DEFAULT NULL,
                    `welcome_intro`   TEXT,
                    `preset_chat_1`   TEXT,
                    `preset_chat_2`   TEXT,
                    `preset_chat_3`   TEXT,
                    `api_base_url`    VARCHAR(512) DEFAULT NULL,
                    `upstream_model`  VARCHAR(256) DEFAULT NULL,
                    `api_key`         TEXT,
                    `hermes_profile`  VARCHAR(128) DEFAULT NULL,
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
        tryAlterAddColumn(db, "chat_session",      "agent_code",    "VARCHAR(128) NOT NULL DEFAULT \"\"");
        tryAlterAddColumn(db, "chat_session",      "hermes_profile","VARCHAR(128) NOT NULL DEFAULT \"\"");
        tryAlterAddColumn(db, "chat_session",      "agent_name",    "VARCHAR(256) NOT NULL DEFAULT \"\"");
        tryAlterAddColumn(db, "chat_session",      "session_goal",  "TEXT");
        tryAlterAddColumn(db, "chat_activity_log", "user_id",       "VARCHAR(64) NOT NULL DEFAULT \"1\"");
        tryAlterAddColumn(db, "message_feedback",  "user_id",       "VARCHAR(64) NOT NULL DEFAULT \"1\"");
        tryAlterAddColumn(db, "chat_message",      "thinking_mode", "VARCHAR(16)");
        tryAlterAddColumn(db, "chat_message",      "think_content", "TEXT");
        tryAlterAddColumn(db, "chat_message",      "entity_cards",  "TEXT");
        tryAlterAddColumn(db, "chat_message",      "intent_analysis", "TEXT");
        tryAlterAddColumn(db, "agent_registry",    "welcome_title",   "VARCHAR(512) DEFAULT NULL");
        tryAlterAddColumn(db, "agent_registry",    "welcome_intro",   "TEXT");
        tryAlterAddColumn(db, "agent_registry",    "api_base_url",    "VARCHAR(512) DEFAULT NULL");
        tryAlterAddColumn(db, "agent_registry",    "upstream_model",  "VARCHAR(256) DEFAULT NULL");
        tryAlterAddColumn(db, "agent_registry",    "api_key",         "TEXT");
        tryAlterAddColumn(db, "agent_registry",    "preset_chat_1",   "TEXT");
        tryAlterAddColumn(db, "agent_registry",    "preset_chat_2",   "TEXT");
        tryAlterAddColumn(db, "agent_registry",    "preset_chat_3",   "TEXT");
        tryAlterAddColumn(db, "agent_registry",    "hermes_profile",    "VARCHAR(128) DEFAULT NULL");
        tryAlterDropColumn(db, "agent_registry",   "external_app_name");
        tryAlterDropColumn(db, "agent_registry",   "external_app_url");
        tryAlterAddColumn(db, "chat_message",      "tool_calls",        "TEXT");
        tryAlterAddColumn(db, "chat_message",      "usage_json",        "TEXT");
        tryAlterAddColumn(db, "chat_message",      "suggestions_json",  "TEXT");
        tryAlterAddColumn(db, "chat_message",      "status",            "VARCHAR(32) NOT NULL DEFAULT 'completed'");
        tryAlterAddColumn(db, "chat_message",      "run_id",            "VARCHAR(64) DEFAULT NULL");
        // 多轮工具结果 / 长回答易超过 TEXT(64KB)，落库失败会在流末尾误报「中断」
        tryAlterModifyColumn(db, "chat_message",    "content",           "LONGTEXT NOT NULL");
        tryAlterModifyColumn(db, "chat_message",    "tool_calls",        "LONGTEXT");
        tryAlterModifyColumn(db, "chat_message",    "think_content",     "LONGTEXT");
        tryAlterModifyColumn(db, "chat_message",    "entity_cards",      "LONGTEXT");
        tryAlterModifyColumn(db, "chat_message",    "intent_analysis",   "LONGTEXT");
        // 毫秒精度，避免同秒内 user/assistant 仅靠无序 UUID 排序导致问答颠倒
        tryAlterModifyColumn(db, "chat_message",    "created_at",        "DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)");
        tryAlterModifyColumn(db, "chat_activity_log", "llm_response_text", "LONGTEXT");
        tryAlterModifyColumn(db, "chat_activity_log", "llm_request_json",  "LONGTEXT");
        tryAlterModifyColumn(db, "chat_activity_log", "user_content",      "LONGTEXT NOT NULL");
        tryAlterAddColumn(db, "chat_activity_log", "thinking_mode", "VARCHAR(16)");
        tryAlterAddColumn(db, "chat_activity_log", "think_content", "TEXT");
        tryAlterAddColumn(db, "data_file",         "detail_text",   "TEXT");
        tryAlterAddColumn(db, "data_file",         "detail_json",   "LONGTEXT");
        tryAlterModifyColumn(db, "data_file",      "detail_text",   "LONGTEXT");
        tryAlterAddColumn(db, "data_file",         "user_id",       "VARCHAR(64) NOT NULL DEFAULT \"1\"");
        tryAlterAddColumn(db, "data_file",         "object_key",    "VARCHAR(1024) DEFAULT NULL");
        tryAlterAddColumn(db, "data_file",         "content_type",  "VARCHAR(256) DEFAULT NULL");
        tryAlterAddColumn(db, "data_file",         "size_bytes",    "BIGINT DEFAULT NULL");
        tryAlterAddColumn(db, "data_file",         "public_token",  "VARCHAR(64) DEFAULT NULL");
        tryAlterAddColumn(db, "data_file",         "folder_path",   "VARCHAR(512) NOT NULL DEFAULT \"\"");
        tryAlterAddIndex(db, "data_file", "idx_user_id", "KEY `idx_user_id` (`user_id`)");
        tryAlterAddIndex(db, "data_file", "uk_public_token", "UNIQUE KEY `uk_public_token` (`public_token`)");
        tryAlterAddIndex(db, "data_file", "idx_user_folder", "KEY `idx_user_folder` (`user_id`, `folder_path`(191))");

        seedUiWelcomeAndToolsIfEmpty();
        removePlaceholderDataFiles();
        seedDataPortraitIfEmpty();
        seedModelRegistryIfEmpty();
        seedDatasetRegistryIfEmpty();
        removeDefaultSeedAgents();
        renameQianxunBrandToDigitalOfficer();
    }

    /** 已有库：品牌名与欢迎区文案对齐「数智干警」口径 */
    private void renameQianxunBrandToDigitalOfficer() {
        uiConfigRepository.findValue("welcome.greeting").ifPresent(greeting -> {
            if (greeting.contains("千寻") || greeting.contains("数字干警")) {
                uiConfigRepository.upsert("welcome.greeting",
                        greeting.replace("千寻问答助手", "数智干警")
                                .replace("千寻", "数智干警")
                                .replace("数字干警", "数智干警"));
            }
        });
        uiConfigRepository.findValue("welcome.disclaimer").ifPresent(disclaimer -> {
            if (disclaimer.contains("大模型")
                    || disclaimer.contains("仔细甄别")
                    || disclaimer.contains("AI 智能体生成")) {
                uiConfigRepository.upsert("welcome.disclaimer", "内容由 AI 生成，请结合业务判断后使用。");
            }
        });
        uiConfigRepository.findValue("welcome.capability").ifPresent(capability -> {
            if (capability.contains("知识和灵感")
                    || capability.contains("学习你的语言")
                    || capability.contains("～")) {
                uiConfigRepository.upsert(
                        "welcome.capability",
                        "把模糊需求理清，拆成可执行任务，再交给合适的智能体去完成。"
                );
            }
        });
        uiConfigRepository.findValue("welcome.recommend_label").ifPresent(label -> {
            if (label.contains("你可以这样问我")) {
                uiConfigRepository.upsert("welcome.recommend_label", "试试这样开始：");
            }
        });
        datasetRegistryRepository.findByCode("qianxun-default-dataset").ifPresent(item -> {
            if (item.name() != null && item.name().contains("数字干警")) {
                datasetRegistryRepository.updateByCode(new DatasetRegistryItem(
                        item.id(), item.code(),
                        item.name().replace("数字干警", "数智干警"),
                        item.description(), item.sourceType(), item.sourceRef(),
                        item.docCount(), item.enabled(), item.createdAt(), Instant.now()
                ));
            }
        });
    }

    private void seedUiWelcomeAndToolsIfEmpty() {
        if (uiConfigRepository.count() == 0) {
            uiConfigRepository.upsert("welcome.disclaimer", "内容由 AI 生成，请结合业务判断后使用。");
            uiConfigRepository.upsert("welcome.greeting", "你好，我是数智干警");
            uiConfigRepository.upsert(
                    "welcome.capability",
                    "把模糊需求理清，拆成可执行任务，再交给合适的智能体去完成。"
            );
            uiConfigRepository.upsert("welcome.recommend_label", "试试这样开始：");
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
        seedOfficerPresetsIfMissing();
        int toolRows = 0;
        for (ToolDisplayName row : ClaudeCodeToolCatalog.seedRows()) {
            try {
                toolDisplayNameRepository.insert(row);
                toolRows++;
            } catch (Exception ex) {
                log.debug("tool_display_name 写入跳过 {}: {}", row.toolCode(), ex.toString());
            }
        }
        if (toolRows > 0) {
            log.info("tool_display_name 已同步 Claude Code 工具中文名: {} 条", toolRows);
            toolDisplayNames.refresh();
        }
    }

    private void seedOfficerPresetsIfMissing() {
        boolean missing = !uiConfigRepository.hasKey(WelcomeOfficerPresets.KEY_1)
                || !uiConfigRepository.hasKey(WelcomeOfficerPresets.KEY_2)
                || !uiConfigRepository.hasKey(WelcomeOfficerPresets.KEY_3);
        if (!missing) {
            return;
        }
        String[] fallback = WelcomeOfficerPresets.resolve(
                uiConfigRepository.getOrEmpty(WelcomeOfficerPresets.KEY_1),
                uiConfigRepository.getOrEmpty(WelcomeOfficerPresets.KEY_2),
                uiConfigRepository.getOrEmpty(WelcomeOfficerPresets.KEY_3),
                suggestedQuestionRepository.listEnabledOrderBySort().stream()
                        .map(q -> q.text() == null ? "" : q.text())
                        .toList()
        );
        if (!uiConfigRepository.hasKey(WelcomeOfficerPresets.KEY_1)) {
            uiConfigRepository.upsert(WelcomeOfficerPresets.KEY_1, fallback[0]);
        }
        if (!uiConfigRepository.hasKey(WelcomeOfficerPresets.KEY_2)) {
            uiConfigRepository.upsert(WelcomeOfficerPresets.KEY_2, fallback[1]);
        }
        if (!uiConfigRepository.hasKey(WelcomeOfficerPresets.KEY_3)) {
            uiConfigRepository.upsert(WelcomeOfficerPresets.KEY_3, fallback[2]);
        }
        log.info("数智干警预置对话已写入");
    }

    /** 中间数据不再写入占位样例；顺带清掉历史「XXXX人员情报」假数据。 */
    private void removePlaceholderDataFiles() {
        String db = properties.getDb();
        try {
            int n = jdbcTemplate.update(
                    "DELETE FROM `" + db + "`.`data_file` WHERE `name` LIKE ?",
                    "XXXX人员情报%"
            );
            if (n > 0) {
                log.info("已清除中间数据占位样例: {} 条", n);
            }
        } catch (Exception ex) {
            log.warn("清除中间数据占位样例失败: {}", ex.toString());
        }
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
                new ModelRegistryItem(newId(), "claude-sonnet-4-5", "Agent（Claude Code）", "anthropic",
                        "claude-code", 200000, 16384, true, now, now)
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

    /** 去掉历史默认种子智能体，智能体中心只保留用户自行注册的条目。 */
    private void removeDefaultSeedAgents() {
        int removed = 0;
        for (String code : List.of("qianxun-main", "zhiguan", "market-agent")) {
            try {
                removed += agentRegistryRepository.deleteByCode(code);
            } catch (Exception ex) {
                log.debug("删除默认种子智能体 {} 失败（忽略）: {}", code, ex.toString());
            }
        }
        if (removed > 0) {
            log.info("已删除智能体中心默认种子智能体 {} 条", removed);
        }
    }

    private void seedAdminUserIfMissing() {
        QianxunProperties.Auth auth = properties.getAuth();
        String username = auth.getDefaultUsername() == null ? "" : auth.getDefaultUsername().trim();
        String password = auth.getDefaultPassword() == null ? "" : auth.getDefaultPassword();
        if (username.isEmpty() || password.isEmpty()) {
            log.warn("未配置默认管理员用户名或密码，跳过 app_user 种子");
            return;
        }
        if (appUserRepository.findByUsername(username).isPresent()) {
            return;
        }
        String userId = auth.getDefaultUserId() == null || auth.getDefaultUserId().isBlank()
                ? "1"
                : auth.getDefaultUserId().trim();
        String display = auth.getDefaultDisplayName() == null || auth.getDefaultDisplayName().isBlank()
                ? "管理员"
                : auth.getDefaultDisplayName().trim();
        Instant now = Instant.now();
        try {
            appUserRepository.insert(new AppUser(
                    userId,
                    username,
                    display,
                    passwordEncoder.encode(password),
                    UserRoles.ADMIN,
                    true,
                    now,
                    now
            ));
            log.info("已写入种子管理员账号: {} (id={})", username, userId);
        } catch (Exception ex) {
            log.warn("写入种子管理员失败（可能已存在）: {}", ex.toString());
        }
    }

    private void seedDatasetRegistryIfEmpty() {
        if (datasetRegistryRepository.count() > 0) {
            return;
        }
        Instant now = Instant.now();
        List<DatasetRegistryItem> seeds = List.of(
                new DatasetRegistryItem(newId(), "qianxun-default-dataset", "数智干警默认数据集",
                        "默认知识数据集，覆盖画像样例。", "tidb", "qianxun.data_portrait_point", 300, true, now, now),
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

    private void tryAlterDropColumn(String db, String table, String column) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE `" + db + "`.`" + table + "` DROP COLUMN `" + column + "`"
            );
            log.info("ALTER TABLE {}.{}: 已删除列 {}", db, table, column);
        } catch (Exception ex) {
            log.debug("ALTER TABLE {}.{} DROP COLUMN {} 已跳过（列可能不存在）: {}", db, table, column, ex.getMessage());
        }
    }

    private void tryAlterAddIndex(String db, String table, String indexName, String indexDef) {
        try {
            jdbcTemplate.execute("ALTER TABLE `" + db + "`.`" + table + "` ADD " + indexDef);
            log.info("ALTER TABLE {}.{}: 已添加索引 {}", db, table, indexName);
        } catch (Exception ex) {
            log.debug("ALTER TABLE {}.{} ADD {} 已跳过（索引可能已存在）: {}", db, table, indexName, ex.getMessage());
        }
    }

    private void tryAlterModifyColumn(String db, String table, String column, String columnDef) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE `" + db + "`.`" + table + "` MODIFY COLUMN `" + column + "` " + columnDef
            );
            log.info("ALTER TABLE {}.{}: 已修改列 {} -> {}", db, table, column, columnDef);
        } catch (Exception ex) {
            log.debug("ALTER TABLE {}.{} MODIFY COLUMN {} 已跳过: {}", db, table, column, ex.getMessage());
        }
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}