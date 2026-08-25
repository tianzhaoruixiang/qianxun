package com.qianxun.domain;

import java.time.Instant;

public record ChatMessage(
        String id,
        String sessionId,
        String role,
        String content,
        /** 历史列，已不再写入深度思考模式 */
        String thinkingMode,
        /** 历史列，已不再写入思考过程 */
        String thinkContent,
        /** 历史列，已不再写入实体卡片 */
        String entityCardsJson,
        /** 历史列，已不再写入意图分析 */
        String intentAnalysisJson,
        /** assistant：工具调用时间线 JSON 数组 */
        String toolCallsJson,
        /** assistant：上下文占用 JSON */
        String usageJson,
        /** assistant：下一步建议 JSON 数组 */
        String suggestionsJson,
        Instant createdAt,
        /** completed | streaming | cancelled | error；旧行为空视为 completed */
        String status,
        /** 流式生成 run id；非流式可空 */
        String runId
) {
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_STREAMING = "streaming";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_ERROR = "error";

    public boolean isStreaming() {
        return STATUS_STREAMING.equalsIgnoreCase(normalizedStatus());
    }

    public String normalizedStatus() {
        if (status == null || status.isBlank()) {
            return STATUS_COMPLETED;
        }
        return status.trim();
    }
}
