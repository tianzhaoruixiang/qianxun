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
        // 即使 depends_on 已等到 Doris healthy，偶发的网络抖动仍可能导致连接失败；
        // 最多重试 5 次（每次间隔 10s），覆盖启动窗口期。
        int maxAttempts = 5;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                initSchema();
                return; // 成功则退出
            } catch (Exception ex) {
                if (attempt < maxAttempts) {
                    log.warn("Doris 初始化失败（第 {}/{} 次），10s 后重试: {}", attempt, maxAttempts, ex.getMessage());
                    try { Thread.sleep(10_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else {
                    log.error("Doris 初始化在 {} 次重试后仍失败，应用将退出", maxAttempts);
                    throw new IllegalStateException("Doris schema 初始化失败", ex);
                }
            }
        }
    }

    private void initSchema() {
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

        // chat_message（含 thinking_mode / think_content / entity_cards）
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `%s`.`chat_message` (
                    `id`            VARCHAR(64) NOT NULL,
                    `session_id`    VARCHAR(64) NOT NULL,
                    `role`          VARCHAR(32) NOT NULL,
                    `content`       STRING      NOT NULL,
                    `thinking_mode` VARCHAR(16),
                    `think_content` STRING,
                    `entity_cards`  STRING,
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
        tryAlterAddColumn(db, "chat_message",      "entity_cards", "STRING");
        tryAlterAddColumn(db, "chat_activity_log", "thinking_mode", "VARCHAR(16)");
        tryAlterAddColumn(db, "chat_activity_log", "think_content", "STRING");

        seedDefaultScenariosIfEmpty();
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
                buildOrgResearch(now),
                buildPersonResearch(now),
                buildGroupResearch(now),
                buildEventAnalysis(now),
                buildGeneral(now)
        );
        // 内置种子场景始终以代码为准进行 upsert：
        //   - 不存在 → insert
        //   - 已存在 → update（保持 id 不变，刷新 slot_schema / prompt_template 等字段）
        // 这样修改种子定义后，重启即自动生效，无需手动清表。
        for (IntentScenario s : seeds) {
            try {
                var existing = intentScenarioRepository.findByCode(s.code());
                if (existing.isPresent()) {
                    // 用现有 id 重建一条更新版本的记录
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
                "对特定人物进行全方位画像调研：基本身份信息、证件与联系方式、工作/教育/政治经历、" +
                "各类关系人网络、新闻社交动态、兴趣爱好，以及污点劣迹等全维度信息。",
                List.of(
                        "帮我调研一下张一鸣",
                        "查一下马云的详细信息和关系网络",
                        "整理王兴的工作经历和人脉关系",
                        "调研一下黄仁勋的背景和最新动态",
                        "帮我查下这个人的教育经历和政治关系"
                ),
                List.of(
                        // ── 核心必填 ──────────────────────────────────────────────────────
                        new SlotDefinition("person_name", "string", true,
                                "目标人物姓名（中文或英文均可）", List.of()),
                        new SlotDefinition("affiliation", "string", false,
                                "所属机构/单位/国籍，用于同名消歧，如「字节跳动」「美国」", List.of()),
                        // ── 调研维度（多选枚举）────────────────────────────────────────
                        new SlotDefinition("focus", "enum", false,
                                "调研聚焦维度，默认 all",
                                List.of("basic_info", "credentials", "career", "education",
                                        "political", "relationships", "news_social",
                                        "interests", "misconduct", "all")),
                        // ── 基本信息细化 ───────────────────────────────────────────────
                        new SlotDefinition("gender", "string", false,
                                "已知性别，可辅助消歧", List.of()),
                        new SlotDefinition("birth_year", "string", false,
                                "出生年份或年龄范围，如「1983」「40岁左右」", List.of()),
                        new SlotDefinition("nationality", "string", false,
                                "国籍或民族，如「中国」「美国华裔」", List.of()),
                        // ── 关系人细化 ─────────────────────────────────────────────────
                        new SlotDefinition("relationship_type", "enum", false,
                                "重点关注的关系人类型，默认 all",
                                List.of("family", "political", "business", "classmate_colleague", "all")),
                        // ── 时间范围 ──────────────────────────────────────────────────
                        new SlotDefinition("time_range", "string", false,
                                "动态/新闻关注的时间范围，如「近一年」「2023-2024」", List.of())
                ),
                "person_research_agent",
                """
                        你现在扮演「人物全景调研专家」，请围绕目标人物输出全方位、无死角的深度画像报告。
                        - 目标人物：{{person_name}}
                        - 所属机构/国籍：{{affiliation|未指定}}
                        - 调研维度：{{focus|all}}
                        - 关系人类型：{{relationship_type|all}}
                        - 时间范围：{{time_range|不限}}

                        请按以下框架系统输出（信息缺失请标注「未公开」或「待核实」，切勿编造；
                        涉及敏感信息时注意合规，仅引用公开可查资料）：

                        1. 基本身份信息
                           - 全名（中英文）、曾用名/别名
                           - 性别、出生日期（精确到年月）、年龄
                           - 出生地 / 籍贯 / 国籍
                           - 民族 / 宗教信仰（如公开）
                           - 已知证件号码类型（身份证、护照号段等，仅公开信息）
                           - 已知联系方式（手机号、邮箱等公开信息）
                           - 官方/认证社交账号（微博、微信公众号、Twitter/X、LinkedIn、抖音等）

                        2. 教育经历
                           - 按时间顺序列出各学历（学校、专业、学位、入学-毕业年份）
                           - 海外留学经历
                           - 导师 / 同学中的关键人物

                        3. 工作与职业经历
                           - 按时间线列出历任职位（机构、职衔、起止时间）
                           - 创业经历（公司名称、角色、结果）
                           - 兼职、顾问、董事会席位
                           - 代表性成就与里程碑事件

                        4. 政治经历与背景
                           - 党籍/政治立场
                           - 担任的政治职务（人大代表、政协委员、党委角色等）
                           - 参与的政治活动、捐款记录（公开）
                           - 政治倾向与核心主张

                        5. 关系人网络
                           5a. 家庭关系
                               - 配偶（姓名、职业）
                               - 子女（数量、年龄段、从事领域）
                               - 父母/兄弟姐妹中的公众人物
                           5b. 政治关系人
                               - 核心政治盟友 / 庇护人
                               - 政治对手
                           5c. 商业利益关系人
                               - 主要商业合作伙伴、投资人、被投资方
                               - 利益输送/股权关联（如公开）
                           5d. 同学/同事网络
                               - 重要校友（同届/师兄弟）
                               - 历任同事中的关键人物

                        6. 新闻与社交动态
                           - 近期（{{time_range|一年内}}）重要新闻事件（按时间倒序）
                           - 本人公开发言、采访、演讲摘要
                           - 社交媒体高互动内容/争议言论
                           - 媒体对其的主要评价倾向

                        7. 兴趣爱好与个人风格
                           - 已公开的兴趣爱好（运动、艺术、收藏等）
                           - 个人价值观与公开主张
                           - 出行偏好、生活方式（如公开）

                        8. 污点、劣迹与争议
                           - 涉及诉讼/仲裁/监管调查（含结果）
                           - 商业失败、违规记录
                           - 公开丑闻或舆论负面事件
                           - 失信/限消/黑名单记录（如可查）

                        9. 综合评估与高价值线索
                           - 人物综合影响力评级（政治/商业/社会各维度）
                           - 值得深入追踪的 5 条关键线索或待核实信息
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
                "调研特定人群（消费者/用户/职业群体/代际群体等）的地域分布、职业构成、行为习惯、消费模式、深层需求与行动动机。",
                List.of(
                        "帮我调研一下00后大学生的消费习惯",
                        "分析一线城市职场妈妈的需求和痛点",
                        "研究下中国中产阶级的行为习惯",
                        "了解一下小镇青年的娱乐和购物偏好",
                        "调研银发族在数字支付方面的行为特征"
                ),
                List.of(
                        new SlotDefinition("group_type", "string", true,
                                "目标群体描述，如「00后大学生」「一线城市职场女性」「农村老年人」", List.of()),
                        new SlotDefinition("country", "string", false,
                                "所属国家或地区，默认中国", List.of()),
                        new SlotDefinition("city", "string", false,
                                "城市或城市级别，如「上海」「新一线城市」「三四线城市」", List.of()),
                        new SlotDefinition("occupation", "string", false,
                                "职业类型或行业，如「互联网从业者」「教师」「个体经营者」", List.of()),
                        new SlotDefinition("focus", "enum", false,
                                "调研重点维度，默认 all",
                                List.of("geo_distribution", "occupation", "behavior",
                                        "consumption", "motivation", "all")),
                        new SlotDefinition("time_range", "string", false,
                                "时间范围，如「近三年」「2024」", List.of())
                ),
                "group_research_agent",
                """
                        你现在扮演「群体洞察研究员」，请围绕目标人群完成深度调研报告。
                        - 目标群体：{{group_type}}
                        - 国家/地区：{{country|中国}}
                        - 城市/区域：{{city|不限}}
                        - 职业类型：{{occupation|不限}}
                        - 调研重点：{{focus|all}}
                        - 时间范围：{{time_range|不限}}

                        请按以下维度系统输出（缺失数据请标注「数据不足」或「待补充」，切勿编造）：

                        1. 群体画像概览
                           - 规模估算与人口统计特征（年龄、性别、学历）
                           - 地域分布（国家 / 省级 / 城市级别）

                        2. 职业与收入特征
                           - 主要职业构成与分布
                           - 收入水平及波动区间

                        3. 行为习惯
                           - 日常作息与生活方式
                           - 媒体与内容消费习惯（使用平台、时长、偏好内容类型）
                           - 社交行为与圈层特征

                        4. 消费习惯
                           - 主要消费品类与支出优先级
                           - 品牌偏好与价格敏感度
                           - 购买决策影响因素与渠道偏好（线上 / 线下 / 社交电商）

                        5. 需求与动机
                           - 核心诉求（功能性 / 情感性 / 社会性）
                           - 主要痛点与未被满足的需求
                           - 价值观取向与消费驱动力

                        6. 趋势与机会
                           - 该群体近 1-2 年的关键变化趋势
                           - 可挖掘的市场机会或值得关注的风险点
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
                "对特定事件（政治、经济、社会、行业、突发等）进行多维度深度分析：背景溯源、时间线梳理、成因剖析、影响评估、各方反应与趋势研判。",
                List.of(
                        "帮我分析一下SVB银行倒闭事件",
                        "分析中美贸易摩擦对科技行业的影响",
                        "解读最近的美联储加息决定",
                        "帮我梳理一下俄乌冲突的事件脉络",
                        "分析拼多多出海temu的市场策略事件",
                        "帮我分析一下最近的AI监管政策出台背景"
                ),
                List.of(
                        new SlotDefinition("event_name", "string", true,
                                "事件名称或核心描述，如「SVB银行倒闭」「中美贸易战」「某政策出台」", List.of()),
                        new SlotDefinition("event_time", "string", false,
                                "事件发生时间或时间段，如「2023年3月」「近两年」", List.of()),
                        new SlotDefinition("event_location", "string", false,
                                "事件发生地点或涉及地区，如「美国」「东南亚」「全球」", List.of()),
                        new SlotDefinition("involved_parties", "string", false,
                                "关键涉事主体（人物、机构、国家等），多个用逗号分隔", List.of()),
                        new SlotDefinition("domain", "enum", false,
                                "事件所属领域，默认 auto 自动判断",
                                List.of("political", "economic", "social", "tech",
                                        "industry", "emergency", "auto")),
                        new SlotDefinition("analysis_focus", "enum", false,
                                "分析侧重维度，默认 all",
                                List.of("background", "timeline", "cause",
                                        "impact", "response", "trend", "all")),
                        new SlotDefinition("time_range", "string", false,
                                "分析覆盖的时间跨度，如「事件发生后6个月」「近一年」", List.of())
                ),
                "event_analysis_agent",
                """
                        你现在扮演「事件深度分析师」，请对目标事件进行系统性、多维度的深度分析。
                        - 事件：{{event_name}}
                        - 发生时间：{{event_time|不限}}
                        - 涉及地区：{{event_location|不限}}
                        - 关键涉事方：{{involved_parties|不限}}
                        - 事件领域：{{domain|auto}}
                        - 分析重点：{{analysis_focus|all}}
                        - 分析时间跨度：{{time_range|不限}}

                        请按以下框架系统输出（缺失信息标注「资料不足」，切勿编造数据）：

                        1. 事件概述
                           - 一段话核心摘要（What / When / Where / Who）
                           - 事件性质与重要性评级（高 / 中 / 低）

                        2. 背景与根源
                           - 宏观背景（历史、政策、市场或技术环境）
                           - 直接导火索与深层结构性原因

                        3. 事件时间线
                           - 按时间顺序列出关键节点与转折（格式：时间 → 事件节点）

                        4. 核心涉事主体
                           - 各主体角色、立场与核心利益
                           - 主要决策与行动

                        5. 影响分析
                           - 直接影响（短期，事件即时效应）
                           - 间接影响（中长期，行业、经济、政策、社会层面）
                           - 影响范围（地域 / 行业 / 人群）

                        6. 各方反应与应对
                           - 政府 / 监管机构的回应措施
                           - 市场与行业的应对动作
                           - 公众舆论与媒体反应

                        7. 趋势研判与后续展望
                           - 最可能的演变路径（3 种情景：乐观 / 基准 / 悲观）
                           - 关键风险点与不确定因素
                           - 值得持续跟踪的 5 个核心指标或信号
                        """,
                Map.of(),
                185,
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
