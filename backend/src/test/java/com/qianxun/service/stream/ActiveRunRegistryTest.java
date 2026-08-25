package com.qianxun.service.stream;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveRunRegistryTest {

    @Test
    void tryBegin_rejectsSecondRunningOnSameSession() {
        ActiveRunRegistry registry = new ActiveRunRegistry();
        Optional<ChatRun> first = registry.tryBegin("s1", "u1");
        Optional<ChatRun> second = registry.tryBegin("s1", "u1");
        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        assertThat(registry.isStreaming("s1")).isTrue();
    }

    @Test
    void tryBegin_allowsNewRunAfterFinish() {
        ActiveRunRegistry registry = new ActiveRunRegistry();
        ChatRun first = registry.tryBegin("s1", "u1").orElseThrow();
        registry.finish(first, ChatRun.Status.COMPLETED);
        assertThat(registry.isStreaming("s1")).isFalse();
        Optional<ChatRun> second = registry.tryBegin("s1", "u1");
        assertThat(second).isPresent();
        assertThat(second.get().runId()).isNotEqualTo(first.runId());
        assertThat(registry.isStreaming("s1")).isTrue();
    }

    @Test
    void publish_survivesDeadSubscriber() throws Exception {
        ChatRun run = new ChatRun("s1", "u1");
        SseEmitter dead = new SseEmitter(0L);
        dead.complete();
        run.addSubscriber(dead, 0);

        AtomicInteger liveCount = new AtomicInteger();
        SseEmitter live = new SseEmitter(0L) {
            @Override
            public void send(SseEventBuilder builder) {
                liveCount.incrementAndGet();
            }
        };
        run.addSubscriber(live, 0);
        run.publish("token", Map.of("text", "hi"));
        assertThat(liveCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(run.lastSeq()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void requestCancel_runsInterruptHookOnce() {
        ChatRun run = new ChatRun("s1", "u1");
        AtomicInteger hits = new AtomicInteger();
        run.onInterrupt(hits::incrementAndGet);
        run.requestCancel();
        run.requestCancel();
        assertThat(run.isCancelRequested()).isTrue();
        assertThat(hits.get()).isEqualTo(1);
    }
}
