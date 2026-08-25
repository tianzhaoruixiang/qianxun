package com.qianxun.web.dto;

import java.util.List;
import java.util.Map;

public record RunMetricsResponse(
        int runningCount,
        int totalTracked,
        long uptimeMs,
        Map<String, Long> statusCounts,
        List<RunSummaryResponse> running
) {}
