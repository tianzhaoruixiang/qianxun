package com.qianxun.service.stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTaskStateTest {

    @Test
    void submittedCanWorkOrCancelOrReject() {
        assertThat(AgentTaskState.SUBMITTED.canTransitionTo(AgentTaskState.WORKING)).isTrue();
        assertThat(AgentTaskState.SUBMITTED.canTransitionTo(AgentTaskState.CANCELED)).isTrue();
        assertThat(AgentTaskState.SUBMITTED.canTransitionTo(AgentTaskState.REJECTED)).isTrue();
        assertThat(AgentTaskState.SUBMITTED.canTransitionTo(AgentTaskState.COMPLETED)).isFalse();
    }

    @Test
    void workingCanCompleteFailCancelOrInput() {
        assertThat(AgentTaskState.WORKING.canTransitionTo(AgentTaskState.COMPLETED)).isTrue();
        assertThat(AgentTaskState.WORKING.canTransitionTo(AgentTaskState.FAILED)).isTrue();
        assertThat(AgentTaskState.WORKING.canTransitionTo(AgentTaskState.CANCELED)).isTrue();
        assertThat(AgentTaskState.WORKING.canTransitionTo(AgentTaskState.INPUT_REQUIRED)).isTrue();
        assertThat(AgentTaskState.WORKING.canTransitionTo(AgentTaskState.SUBMITTED)).isFalse();
    }

    @Test
    void terminalCannotMove() {
        assertThat(AgentTaskState.COMPLETED.canTransitionTo(AgentTaskState.WORKING)).isFalse();
        assertThat(AgentTaskState.FAILED.terminal()).isTrue();
        assertThat(AgentTaskState.parse("input-required")).isEqualTo(AgentTaskState.INPUT_REQUIRED);
        assertThat(AgentTaskState.parse("cancelled")).isEqualTo(AgentTaskState.CANCELED);
        assertThat(AgentTaskState.SUBMITTED.crewStatus()).isEqualTo("awaiting");
        assertThat(AgentTaskState.WORKING.crewStatus()).isEqualTo("running");
        assertThat(AgentTaskState.FAILED.crewStatus()).isEqualTo("error");
    }

    @Test
    void taskTransitionHonorsMachine() {
        AgentTask task = new AgentTask("u1", "run1", "sess1", "law", "法务", "写纪要");
        assertThat(task.state()).isEqualTo(AgentTaskState.SUBMITTED);
        assertThat(task.childSessionId()).startsWith(AgentTask.SESSION_PREFIX);
        assertThat(task.transition(AgentTaskState.WORKING)).isTrue();
        assertThat(task.transition(AgentTaskState.COMPLETED)).isTrue();
        assertThat(task.transition(AgentTaskState.CANCELED)).isFalse();
    }

    @Test
    void awaitTerminalReturnsAfterComplete() {
        AgentTask task = new AgentTask("u1", "run1", "sess1", "law", "法务", "写纪要");
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            task.transition(AgentTaskState.WORKING);
            task.transition(AgentTaskState.COMPLETED);
        });
        t.start();
        assertThat(task.awaitTerminal(2_000, () -> false)).isTrue();
        assertThat(task.state()).isEqualTo(AgentTaskState.COMPLETED);
    }

    @Test
    void registryFindsOldestActiveByAgent() {
        AgentTaskRegistry registry = new AgentTaskRegistry();
        AgentTask first = new AgentTask("u1", "run1", "sess1", "bao", "报销", "一");
        AgentTask done = new AgentTask("u1", "run1", "sess1", "bao", "报销", "旧");
        done.transition(AgentTaskState.WORKING);
        done.transition(AgentTaskState.COMPLETED);
        AgentTask other = new AgentTask("u1", "run1", "sess1", "law", "法务", "二");
        registry.put(done);
        registry.put(first);
        registry.put(other);
        assertThat(registry.findActive("run1", "bao").orElseThrow().id()).isEqualTo(first.id());
        assertThat(registry.findActive("run1", "law").orElseThrow().id()).isEqualTo(other.id());
        first.transition(AgentTaskState.WORKING);
        first.transition(AgentTaskState.COMPLETED);
        assertThat(registry.findActive("run1", "bao")).isEmpty();
    }
}
