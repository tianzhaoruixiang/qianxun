package com.qianxun.service.stream;

import com.qianxun.context.TraceContext;
import com.qianxun.util.SseKeepAlive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一轮助手生成任务：与具体 SSE 连接解耦，支持多订阅者再附着。
 */
public final class ChatRun {

    private static final Logger log = LoggerFactory.getLogger(ChatRun.class);
    private static final int MAX_BUFFER = 1024;

    public enum Status {
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public record BufferedEvent(long seq, String name, Map<String, Object> data) {}

    private final String runId;
    private final String sessionId;
    private final String userId;
    private final String traceId;
    private final long startedAtMs;
    private volatile String assistantMessageId = "";
    private volatile String hermesProfile = "";
    private volatile String agentCode = "";
    private volatile String modelCode = "";
    private volatile int toolCallCount = 0;
    private volatile int delegationCount = 0;
    private volatile long lastEventAtMs;
    private volatile Status status = Status.RUNNING;
    private final AtomicLong seq = new AtomicLong(0);
    private final List<BufferedEvent> buffer = new ArrayList<>();
    private final CopyOnWriteArrayList<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    /** 停止时打断上游连接（关闭 HTTP body / abort WebSocket 等） */
    private final AtomicReference<Runnable> interruptHook = new AtomicReference<>();
    private volatile String contentSnapshot = "";
    private final Object bufferLock = new Object();

    public ChatRun(String sessionId, String userId) {
        this.runId = UUID.randomUUID().toString().replace("-", "");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.userId = userId == null ? "" : userId;
        this.traceId = TraceContext.ensure();
        this.startedAtMs = System.currentTimeMillis();
        this.lastEventAtMs = this.startedAtMs;
    }

    public String runId() {
        return runId;
    }

    public String sessionId() {
        return sessionId;
    }

    public String userId() {
        return userId;
    }

    public String traceId() {
        return traceId;
    }

    public long startedAtMs() {
        return startedAtMs;
    }

    public long lastEventAtMs() {
        return lastEventAtMs;
    }

    public String hermesProfile() {
        return hermesProfile;
    }

    public void setHermesProfile(String profile) {
        this.hermesProfile = profile == null ? "" : profile.trim();
    }

    public String agentCode() {
        return agentCode;
    }

    public void setAgentCode(String code) {
        this.agentCode = code == null ? "" : code.trim();
    }

    public String modelCode() {
        return modelCode;
    }

    public void setModelCode(String code) {
        this.modelCode = code == null ? "" : code.trim();
    }

    public int toolCallCount() {
        return toolCallCount;
    }

    public int delegationCount() {
        return delegationCount;
    }

    public void incrementToolCalls(int delta) {
        if (delta > 0) {
            this.toolCallCount += delta;
        }
    }

    public void incrementDelegations(int delta) {
        if (delta > 0) {
            this.delegationCount += delta;
        }
    }

    public String assistantMessageId() {
        return assistantMessageId;
    }

    public void setAssistantMessageId(String id) {
        this.assistantMessageId = id == null ? "" : id;
    }

    public Status status() {
        return status;
    }

    public long lastSeq() {
        return seq.get();
    }

    public String contentSnapshot() {
        return contentSnapshot;
    }

    public void setContentSnapshot(String content) {
        this.contentSnapshot = content == null ? "" : content;
    }

    public boolean isRunning() {
        return status == Status.RUNNING;
    }

    public boolean isCancelRequested() {
        return cancelRequested.get();
    }

    /**
     * 注册停止时的上游中断钩子；后写覆盖前写。Run 结束后应 {@link #clearInterruptHook()}。
     */
    public void onInterrupt(Runnable hook) {
        interruptHook.set(hook);
    }

    public void clearInterruptHook() {
        interruptHook.set(null);
    }

    public void requestCancel() {
        cancelRequested.set(true);
        Runnable hook = interruptHook.getAndSet(null);
        if (hook == null) {
            return;
        }
        try {
            hook.run();
        } catch (Exception ex) {
            log.debug("interrupt hook failed session={} run={}: {}", sessionId, runId, ex.toString());
        }
    }

    public void markStatus(Status next) {
        if (next != null) {
            this.status = next;
        }
    }

    /**
     * 发布事件：写入缓冲并 fan-out。单个 subscriber 失败不影响 Run。
     * 若已请求取消且非终态事件，抛出 {@link CancellationException}。
     */
    public long publish(String name, Map<String, Object> data) {
        if (cancelRequested.get() && !isTerminalEvent(name)) {
            throw new CancellationException("run cancelled");
        }
        long s = seq.incrementAndGet();
        lastEventAtMs = System.currentTimeMillis();
        Map<String, Object> payload = enrich(data, s);
        BufferedEvent event = new BufferedEvent(s, name, payload);
        synchronized (bufferLock) {
            buffer.add(event);
            while (buffer.size() > MAX_BUFFER) {
                buffer.remove(0);
            }
        }
        for (Subscriber sub : subscribers) {
            trySend(sub, name, payload);
        }
        return s;
    }

    /** 仅 fan-out，不检查取消（用于 cancelled/error/done 终态）。 */
    public long publishTerminal(String name, Map<String, Object> data) {
        long s = seq.incrementAndGet();
        lastEventAtMs = System.currentTimeMillis();
        Map<String, Object> payload = enrich(data, s);
        BufferedEvent event = new BufferedEvent(s, name, payload);
        synchronized (bufferLock) {
            buffer.add(event);
            while (buffer.size() > MAX_BUFFER) {
                buffer.remove(0);
            }
        }
        for (Subscriber sub : subscribers) {
            trySend(sub, name, payload);
        }
        return s;
    }

    public void addSubscriber(SseEmitter emitter, long afterSeq) {
        if (emitter == null) {
            return;
        }
        Subscriber sub = new Subscriber(emitter);
        List<BufferedEvent> replay;
        synchronized (bufferLock) {
            replay = buffer.stream().filter(e -> e.seq() > afterSeq).toList();
        }
        for (BufferedEvent e : replay) {
            trySend(sub, e.name(), e.data());
            if (sub.dead) {
                sub.closeQuietly();
                return;
            }
        }
        if (!isRunning()) {
            // 宽限窗口内已结束：回放后结束连接
            completeEmitter(sub);
            return;
        }
        subscribers.add(sub);
        emitter.onCompletion(() -> removeSubscriber(sub));
        emitter.onTimeout(() -> removeSubscriber(sub));
        emitter.onError(ex -> removeSubscriber(sub));
    }

    public void completeAllSubscribers() {
        for (Subscriber sub : subscribers) {
            completeEmitter(sub);
        }
        subscribers.clear();
    }

    private void removeSubscriber(Subscriber sub) {
        subscribers.remove(sub);
        sub.closeQuietly();
    }

    private Map<String, Object> enrich(Map<String, Object> data, long s) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (data != null) {
            out.putAll(data);
        }
        out.put("seq", s);
        out.put("runId", runId);
        out.put("traceId", traceId);
        out.put("sessionId", sessionId);
        if (hermesProfile != null && !hermesProfile.isBlank()) {
            out.putIfAbsent("hermesProfile", hermesProfile);
        }
        if (agentCode != null && !agentCode.isBlank()) {
            out.putIfAbsent("agentCode", agentCode);
        }
        if (assistantMessageId != null && !assistantMessageId.isBlank()) {
            out.putIfAbsent("assistantMessageId", assistantMessageId);
        }
        return out;
    }

    private static boolean isTerminalEvent(String name) {
        return "done".equals(name) || "error".equals(name) || "cancelled".equals(name);
    }

    private void trySend(Subscriber sub, String name, Map<String, Object> data) {
        if (sub.dead) {
            return;
        }
        try {
            sub.emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException ex) {
            log.debug("SSE subscriber dropped session={} run={}: {}", sessionId, runId, ex.toString());
            sub.dead = true;
            removeSubscriber(sub);
        }
    }

    private void completeEmitter(Subscriber sub) {
        sub.closeQuietly();
        try {
            sub.emitter.complete();
        } catch (Exception ignored) {
            /* already completed */
        }
    }

    private static final class Subscriber {
        private final SseEmitter emitter;
        private final SseKeepAlive keepAlive;
        private volatile boolean dead;

        private Subscriber(SseEmitter emitter) {
            this.emitter = emitter;
            this.keepAlive = new SseKeepAlive(emitter);
        }

        private void closeQuietly() {
            dead = true;
            try {
                keepAlive.close();
            } catch (Exception ignored) {
                /* ignore */
            }
        }
    }
}
