package com.qianxun.web.dto;

import java.util.List;

public record StreamChatRequest(
        String content,
        /**
         * 可选：前端从模型表选择的模型 code（model_registry.code）。
         * 非空时优先使用该模型配置（baseUrl/model/provider）。
         */
        String modelCode,
        /**
         * 可选：从智能体超市进入对话时携带的 agent_registry.code。
         * 对话走该智能体绑定的 Hermes profile。
         */
        String agentCode,
        /**
         * 可选：当前选用的智能体 profile（Dashboard session.create / 建议短请求路由）。
         */
        String hermesProfile,
        /**
         * 可选：本轮聊天框上传的文档 id（data_file.id），须属于当前用户。
         * 未传或为空时不得从「我的网盘」抽取任何文件正文进入智能体上下文。
         */
        List<String> fileIds,
        /**
         * 可选：本轮强制使用的技能名称（须为当前智能体已启用技能）。
         * Dashboard 路径会拼成原生 {@code /{skill-slug} …}，经 {@code slash.exec}
         *（技能场景官方再走 {@code command.dispatch}）下发；非 Dashboard 时回退为注入 SKILL.md。
         */
        String skillName,
        /**
         * 可选：本轮设定的长程目标；非空则写入会话，并下发 Claude Code 原生 {@code /goal &lt;condition&gt;}。
         */
        SessionGoalRequest goal,
        /**
         * 为 true 时清除会话长程目标，并由 Dashboard 原生 {@code /goal clear} 生效。
         */
        Boolean clearGoal,
        /**
         * 为 true 时查询当前会话活跃子智能体与运行中任务，并由 Dashboard 原生
         * {@code /agents}（别名 {@code /tasks}、前端 {@code /task}）斜杠命令生效。
         */
        Boolean agentsStatus,
        /**
         * 可选：显式 Claude Code 斜杠命令（如 {@code /compact}、{@code /mcp}），
         * 优先于从 content 自动识别。
         */
        String slashCommand
) {
}
