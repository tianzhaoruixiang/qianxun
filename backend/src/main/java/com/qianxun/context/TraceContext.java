package com.qianxun.context;

import java.util.UUID;

/**
 * 请求级 traceId，供 SSE 事件、网关调用与日志关联。
 */
public final class TraceContext {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceContext() {}

    public static String get() {
        String id = CURRENT.get();
        return id == null ? "" : id;
    }

    public static void set(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            CURRENT.remove();
        } else {
            CURRENT.set(traceId.trim());
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static String ensure() {
        String id = get();
        if (!id.isBlank()) {
            return id;
        }
        id = newTraceId();
        set(id);
        return id;
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
