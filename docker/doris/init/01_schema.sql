-- 千寻 · Doris 数据库初始化（DDL）
-- 由 docker compose 中的 doris-init 一次性容器在 FE/BE healthy 后自动执行；
-- 也可手动 `mysql -h 127.0.0.1 -P 9030 -u root < 01_schema.sql` 单独执行。
-- 后端启动时还会再做幂等的 schema 校验与默认意图场景种子写入。

CREATE DATABASE IF NOT EXISTS `qianxun`;

-- 会话（user_id 来自外部用户系统，本系统不存储用户信息）
CREATE TABLE IF NOT EXISTS `qianxun`.`chat_session` (
    `id`         VARCHAR(64)   NOT NULL COMMENT '会话 ID',
    `user_id`    VARCHAR(64)   NOT NULL DEFAULT "1" COMMENT '所属用户 ID',
    `title`      VARCHAR(1024) NOT NULL COMMENT '会话标题',
    `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
)
UNIQUE KEY(`id`)
DISTRIBUTED BY HASH(`id`) BUCKETS 8
PROPERTIES ("replication_allocation" = "tag.location.default: 1");

-- 消息（含 thinking_mode 和 think_content，仅 assistant 消息有意义）
CREATE TABLE IF NOT EXISTS `qianxun`.`chat_message` (
    `id`           VARCHAR(64) NOT NULL COMMENT '消息 ID',
    `session_id`   VARCHAR(64) NOT NULL COMMENT '所属会话 ID',
    `role`         VARCHAR(32) NOT NULL COMMENT 'user / assistant / system',
    `content`      STRING      NOT NULL COMMENT '消息正文（assistant 为去除 think 块后的正式回复）',
    `thinking_mode` VARCHAR(16)          COMMENT '"quick" 或 "deep"，user 消息为 null',
    `think_content` STRING               COMMENT 'deep 模式下 AI 的内部推理内容（已去除 <think> 标签）',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
)
UNIQUE KEY(`id`)
DISTRIBUTED BY HASH(`id`) BUCKETS 8
PROPERTIES ("replication_allocation" = "tag.location.default: 1");

-- 意图场景（业务可在线 CRUD，启动时若为空由后端写入默认种子：org_research / person_research / general）
CREATE TABLE IF NOT EXISTS `qianxun`.`intent_scenario` (
    `id`              VARCHAR(64)  NOT NULL COMMENT '主键',
    `code`            VARCHAR(128) NOT NULL COMMENT '场景标识（应用层唯一）',
    `name`            VARCHAR(256) NOT NULL COMMENT '展示名',
    `description`     STRING                COMMENT '场景描述',
    `examples`        STRING                COMMENT '示例话术 JSON 数组',
    `slot_schema`     STRING                COMMENT '槽位定义 JSON 数组',
    `agent_skill`     VARCHAR(256)          COMMENT '映射到 Hermes/LLM 的技能名（可选）',
    `prompt_template` STRING                COMMENT 'system 提示词模板，支持 {{slot}} / {{slot|default}}',
    `extra_params`    STRING                COMMENT '透传给 LLM 的额外参数 JSON',
    `priority`        INT          NOT NULL DEFAULT "100" COMMENT '优先级，越大越先匹配',
    `enabled`         TINYINT      NOT NULL DEFAULT "1"   COMMENT '0/1',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
)
UNIQUE KEY(`id`)
DISTRIBUTED BY HASH(`id`) BUCKETS 4
PROPERTIES ("replication_allocation" = "tag.location.default: 1");

-- 活动日志（含用户归属）
CREATE TABLE IF NOT EXISTS `qianxun`.`chat_activity_log` (
    `id`                   VARCHAR(64)  NOT NULL COMMENT '日志 ID',
    `user_id`              VARCHAR(64)  NOT NULL DEFAULT "1" COMMENT '所属用户 ID',
    `session_id`           VARCHAR(64)  NOT NULL COMMENT '会话 ID',
    `user_message_id`      VARCHAR(64)           COMMENT '用户消息 ID（chat_message.id）',
    `assistant_message_id` VARCHAR(64)           COMMENT 'AI 消息 ID（chat_message.id）',
    `user_content`         STRING       NOT NULL COMMENT '原始用户问题',
    `nlu_intent`           VARCHAR(256)          COMMENT 'NLU 识别的 intent code',
    `nlu_scenario_code`    VARCHAR(128)          COMMENT 'NLU 场景 code',
    `nlu_scenario_name`    VARCHAR(256)          COMMENT 'NLU 场景展示名',
    `nlu_agent_skill`      VARCHAR(256)          COMMENT '对应 agent skill',
    `nlu_confidence`       DOUBLE                COMMENT 'NLU 置信度 0~1',
    `nlu_slots`            STRING                COMMENT 'NLU 提取的槽位 JSON',
    `nlu_missing_slots`    STRING                COMMENT '缺失必填槽位 JSON 数组',
    `nlu_reasoning`        STRING                COMMENT 'NLU 推理说明',
    `nlu_raw_response`     STRING                COMMENT 'NLU LLM 原始输出（调试用）',
    `llm_endpoint`         VARCHAR(512)          COMMENT 'LLM 端点 base URL',
    `llm_model`            VARCHAR(128)          COMMENT '本次使用的模型名',
    `llm_request_json`     STRING                COMMENT '发给 LLM 的完整请求 JSON（含历史 messages）',
    `llm_response_text`    STRING                COMMENT 'LLM 返回的 assistant 完整文本',
    `status`               VARCHAR(32)  NOT NULL DEFAULT 'success' COMMENT 'success / error / mock',
    `error_message`        STRING                COMMENT '错误信息（status=error 时填入）',
    `thinking_mode`        VARCHAR(16)           COMMENT '"quick" 或 "deep"',
    `think_content`        STRING                COMMENT 'deep 模式下 AI 内部推理内容',
    `nlu_duration_ms`      BIGINT                COMMENT 'NLU 阶段耗时（毫秒）',
    `llm_duration_ms`      BIGINT                COMMENT 'LLM 流式阶段耗时（毫秒）',
    `total_duration_ms`    BIGINT                COMMENT '本次问答总耗时（毫秒）',
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
)
UNIQUE KEY(`id`)
DISTRIBUTED BY HASH(`id`) BUCKETS 8
PROPERTIES ("replication_allocation" = "tag.location.default: 1");

-- 用户反馈（含用户归属）
CREATE TABLE IF NOT EXISTS `qianxun`.`message_feedback` (
    `id`              VARCHAR(64)  NOT NULL COMMENT '反馈 ID',
    `user_id`         VARCHAR(64)  NOT NULL DEFAULT "1" COMMENT '所属用户 ID',
    `session_id`      VARCHAR(64)  NOT NULL COMMENT '会话 ID',
    `message_id`      VARCHAR(64)  NOT NULL COMMENT 'AI 消息 ID（chat_message.id）',
    `activity_log_id` VARCHAR(64)           COMMENT '关联的活动日志 ID',
    `feedback_type`   VARCHAR(16)  NOT NULL COMMENT 'like / dislike',
    `feedback_note`   STRING                COMMENT '用户附加备注（可选）',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
)
UNIQUE KEY(`id`)
DISTRIBUTED BY HASH(`id`) BUCKETS 4
PROPERTIES ("replication_allocation" = "tag.location.default: 1");

-- ── 升级迁移（幂等补列）───────────────────────────────────────────────────────
-- init.sh 使用 mysql --force 执行，单条语句失败（如列已存在）不会中止整个脚本。
-- 全新部署：ALTER TABLE 报 "Column already exists"，被 --force 忽略，exit 0。
-- 存量升级：ALTER TABLE 成功添加列，exit 0。
-- 两种情况均可重复执行。

-- chat_session 补充 user_id
ALTER TABLE `qianxun`.`chat_session`
    ADD COLUMN `user_id` VARCHAR(64) NOT NULL DEFAULT "1" COMMENT '所属用户 ID（外部系统传入）';

-- chat_message 补充思考模式列
ALTER TABLE `qianxun`.`chat_message`
    ADD COLUMN `thinking_mode` VARCHAR(16) COMMENT '"quick" 或 "deep"，user 消息为 null';
ALTER TABLE `qianxun`.`chat_message`
    ADD COLUMN `think_content` STRING COMMENT 'deep 模式下 AI 的内部推理内容';

-- chat_activity_log 补充 user_id 与思考模式列
ALTER TABLE `qianxun`.`chat_activity_log`
    ADD COLUMN `user_id` VARCHAR(64) NOT NULL DEFAULT "1" COMMENT '所属用户 ID';
ALTER TABLE `qianxun`.`chat_activity_log`
    ADD COLUMN `thinking_mode` VARCHAR(16) COMMENT '"quick" 或 "deep"';
ALTER TABLE `qianxun`.`chat_activity_log`
    ADD COLUMN `think_content` STRING COMMENT 'deep 模式下 AI 内部推理内容';

-- message_feedback 补充 user_id
ALTER TABLE `qianxun`.`message_feedback`
    ADD COLUMN `user_id` VARCHAR(64) NOT NULL DEFAULT "1" COMMENT '所属用户 ID';
