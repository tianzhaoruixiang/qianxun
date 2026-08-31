package com.qianxun.service;

import com.qianxun.config.QianxunProperties;
import com.qianxun.context.UserContext;
import com.qianxun.domain.AgentRegistryItem;
import com.qianxun.repo.AgentRegistryRepository;
import com.qianxun.service.stream.ActiveRunRegistry;
import com.qianxun.service.stream.AgentTaskRegistry;
import com.qianxun.service.stream.ChatRun;
import com.qianxun.web.dto.AgentTaskResponse;
import com.qianxun.web.dto.SubmitAgentTaskRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTaskServiceTest {

    private final AgentTaskRegistry registry = new AgentTaskRegistry();
    @Mock ActiveRunRegistry activeRunRegistry;
    @Mock AgentRegistryRepository agentRegistryRepository;
    @Mock QianXunServiceChatSession sessionService;
    @Mock QianXunServiceChatStream chatStream;
    private final Executor sameThread = Runnable::run;
    private AgentTaskService service;

    @BeforeEach
    void setUp() {
        UserContext.set("u1", "tester", "测试");
        service = new AgentTaskService(
                registry, activeRunRegistry, agentRegistryRepository, sessionService, chatStream,
                sameThread, new QianxunProperties());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void submitRejectsBlank() {
        assertThatThrownBy(() -> service.submit(new SubmitAgentTaskRequest("", "hi", "r1", "s1")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void submitRejectsNestedTaskSession() {
        assertThatThrownBy(() -> service.submit(new SubmitAgentTaskRequest("law", "hi", "r1", "task-abc")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不能再委派");
    }

    @Test
    void submitRejectsUnknownParentRun() {
        when(activeRunRegistry.findByRunId("r1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(new SubmitAgentTaskRequest("law", "hi", "r1", "sess")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void submitRejectsMissingAgent() {
        ChatRun parent = new ChatRun("sess", "u1");
        when(activeRunRegistry.findByRunId(parent.runId())).thenReturn(Optional.of(parent));
        when(agentRegistryRepository.findByCode("law")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(
                new SubmitAgentTaskRequest("law", "写纪要", parent.runId(), "sess")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void submitCoalescesSameAgentOnSameParentRun() {
        ChatRun parent = new ChatRun("sess", "u1");
        Instant now = Instant.now();
        AgentRegistryItem agent = new AgentRegistryItem(
                "id", "baoxiaozhushou", "报销助手", "assistant", "", "", "m1",
                "", "", "", "", "", "", "", "", "", 1, true, now, now);
        when(activeRunRegistry.findByRunId(parent.runId())).thenReturn(Optional.of(parent));
        when(agentRegistryRepository.findByCode("baoxiaozhushou")).thenReturn(Optional.of(agent));
        when(activeRunRegistry.tryBegin(anyString(), eq("u1"))).thenAnswer(inv ->
                Optional.of(new ChatRun(inv.getArgument(0), inv.getArgument(1))));

        AgentTaskResponse first = service.submit(new SubmitAgentTaskRequest(
                "baoxiaozhushou", "办理报销", parent.runId(), "sess", false));
        AgentTaskResponse second = service.submit(new SubmitAgentTaskRequest(
                "baoxiaozhushou", "再分析一遍", parent.runId(), "sess", false));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(registry.listByParentRun(parent.runId())).hasSize(1);
        assertThat(parent.delegationCount()).isEqualTo(1);
    }
}
