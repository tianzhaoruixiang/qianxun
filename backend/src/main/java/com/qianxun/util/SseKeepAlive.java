package com.qianxun.util;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 向浏览器 SSE 连接定期写心跳，避免 nginx / 网关按「两次读间隔」空闲超时断开。
 * Hermes 工具执行、首 token 等待期间上游可能长时间无数据，必须靠心跳保活。
 *
 * <p>同时发送 SSE comment 与命名 {@code heartbeat} 事件：部分中间件不把 comment 算作活动流量。
 */
public final class SseKeepAlive implements AutoCloseable {

    /** 低于常见 60s 空闲超时，留足余量。 */
    private static final long DEFAULT_INTERVAL_SECONDS = 10;

    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "qianxun-sse-keepalive");
        t.setDaemon(true);
        return t;
    });

    private final ScheduledFuture<?> future;

    public SseKeepAlive(SseEmitter emitter) {
        this(emitter, DEFAULT_INTERVAL_SECONDS);
    }

    public SseKeepAlive(SseEmitter emitter, long intervalSeconds) {
        long interval = Math.max(5, intervalSeconds);
        this.future = SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data(Map.of("ts", System.currentTimeMillis())));
            } catch (Exception ignored) {
                // emitter 已完成或客户端已断开
            }
        }, interval, interval, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        future.cancel(false);
    }
}
