package com.qianxun.service;

import com.qianxun.service.stream.ActiveRunRegistry;
import com.qianxun.service.stream.ChatRun;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RunMetricsService {

    private final ActiveRunRegistry registry;
    private final MeterRegistry meterRegistry;
    private final long startedAt = System.currentTimeMillis();

    public RunMetricsService(ActiveRunRegistry registry, MeterRegistry meterRegistry) {
        this.registry = registry;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void bindGauges() {
        Gauge.builder("qianxun.chat.runs.active", registry, ActiveRunRegistry::runningCount)
                .description("Number of active chat runs")
                .register(meterRegistry);
    }

    public Map<String, Long> statusCounts(List<ChatRun> runs) {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        for (ChatRun.Status s : ChatRun.Status.values()) {
            out.put(s.name(), 0L);
        }
        for (ChatRun r : runs) {
            out.merge(r.status().name(), 1L, Long::sum);
        }
        return out;
    }

    public long uptimeMs() {
        return System.currentTimeMillis() - startedAt;
    }
}
