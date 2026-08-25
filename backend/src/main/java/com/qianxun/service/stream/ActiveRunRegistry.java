package com.qianxun.service.stream;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 进程内进行中的 ChatRun 注册表（单实例）。同会话同时仅允许一个 RUNNING。
 */
@Component
public class ActiveRunRegistry {

    /** 结束后保留多久供迟到再附着（秒） */
    private static final long GRACE_SECONDS = 45;

    private final ConcurrentHashMap<String, ChatRun> bySession = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "qianxun-chat-run-cleaner");
        t.setDaemon(true);
        return t;
    });

    /**
     * 尝试注册新 Run。若该会话已有 RUNNING，返回 empty。
     */
    public Optional<ChatRun> tryBegin(String sessionId, String userId) {
        ChatRun created = new ChatRun(sessionId, userId);
        ChatRun existing = bySession.putIfAbsent(sessionId, created);
        if (existing != null) {
            if (existing.isRunning()) {
                return Optional.empty();
            }
            // 宽限期内的已结束 Run：替换为新 Run
            if (!bySession.replace(sessionId, existing, created)) {
                ChatRun again = bySession.get(sessionId);
                if (again != null && again.isRunning()) {
                    return Optional.empty();
                }
                bySession.put(sessionId, created);
            }
        }
        return Optional.of(created);
    }

    public Optional<ChatRun> findBySession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(bySession.get(sessionId));
    }

    public Optional<ChatRun> findRunning(String sessionId) {
        return findBySession(sessionId).filter(ChatRun::isRunning);
    }

    public boolean isStreaming(String sessionId) {
        return findRunning(sessionId).isPresent();
    }

    public Set<String> streamingSessionIds() {
        return bySession.entrySet().stream()
                .filter(e -> e.getValue().isRunning())
                .map(java.util.Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 当前用户所有 Run（含宽限期内已结束）。 */
    public List<ChatRun> listByUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return bySession.values().stream()
                .filter(r -> userId.equals(r.userId()))
                .sorted((a, b) -> Long.compare(b.startedAtMs(), a.startedAtMs()))
                .toList();
    }

    /** 全局 RUNNING Run。 */
    public List<ChatRun> listRunning() {
        return bySession.values().stream()
                .filter(ChatRun::isRunning)
                .sorted((a, b) -> Long.compare(b.startedAtMs(), a.startedAtMs()))
                .toList();
    }

    public int runningCount() {
        return (int) bySession.values().stream().filter(ChatRun::isRunning).count();
    }

    public void finish(ChatRun run, ChatRun.Status terminalStatus) {
        if (run == null) {
            return;
        }
        run.markStatus(terminalStatus == null ? ChatRun.Status.COMPLETED : terminalStatus);
        run.completeAllSubscribers();
        cleaner.schedule(() -> bySession.remove(run.sessionId(), run), GRACE_SECONDS, TimeUnit.SECONDS);
    }
}
