package com.qianxun.service;

import com.qianxun.config.QianxunProperties;
import com.qianxun.context.UserContext;
import com.qianxun.domain.AgentRegistryItem;
import com.qianxun.domain.ChatSession;
import com.qianxun.repo.AgentRegistryRepository;
import com.qianxun.service.stream.ActiveRunRegistry;
import com.qianxun.service.stream.AgentTask;
import com.qianxun.service.stream.AgentTaskRegistry;
import com.qianxun.service.stream.AgentTaskState;
import com.qianxun.service.stream.ChatRun;
import com.qianxun.web.dto.AgentTaskResponse;
import com.qianxun.web.dto.SubmitAgentTaskRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AgentTaskService {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskService.class);
    static final String TOOL_NAME = "delegate_to_agent";

    private final AgentTaskRegistry registry;
    private final ActiveRunRegistry activeRunRegistry;
    private final AgentRegistryRepository agentRegistryRepository;
    private final QianXunServiceChatSession sessionService;
    private final QianXunServiceChatStream chatStream;
    private final Executor sseExecutor;
    private final QianxunProperties properties;
    private final ConcurrentHashMap<String, Object> submitLocks = new ConcurrentHashMap<>();

    public AgentTaskService(
            AgentTaskRegistry registry,
            ActiveRunRegistry activeRunRegistry,
            AgentRegistryRepository agentRegistryRepository,
            QianXunServiceChatSession sessionService,
            @Lazy QianXunServiceChatStream chatStream,
            @Qualifier("sseExecutor") Executor sseExecutor,
            QianxunProperties properties
    ) {
        this.registry = registry;
        this.activeRunRegistry = activeRunRegistry;
        this.agentRegistryRepository = agentRegistryRepository;
        this.sessionService = sessionService;
        this.chatStream = chatStream;
        this.sseExecutor = sseExecutor;
        this.properties = properties;
    }

    public AgentTaskResponse submit(SubmitAgentTaskRequest request) {
        String userId = UserContext.getCurrentUserId();
        if (request == null || isBlank(request.agentCode()) || isBlank(request.message())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "agentCode 与 message 不能为空");
        }
        String parentRunId = trim(request.parentRunId());
        String parentSessionId = trim(request.parentSessionId());
        if (parentRunId.isEmpty() || parentSessionId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少父会话 run 信息");
        }
        if (AgentTask.isTaskSession(parentSessionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "专业智能体不能再委派专业智能体");
        }
        ChatRun parent = activeRunRegistry.findByRunId(parentRunId)
                .filter(r -> userId.equals(r.userId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "父轮次不存在或已结束"));
        if (!parentSessionId.equals(parent.sessionId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "parentSessionId 与 run 不匹配");
        }
        if (!parent.isRunning()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "父轮次已结束，无法委派");
        }
        AgentRegistryItem agent = agentRegistryRepository.findByCode(request.agentCode().trim())
                .filter(AgentRegistryItem::enabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "专业智能体不存在或未启用"));

        Object lock = submitLocks.computeIfAbsent(parentRunId + "\0" + agent.code(), k -> new Object());
        synchronized (lock) {
            AgentTask inflight = registry.findActive(parentRunId, agent.code()).orElse(null);
            if (inflight != null) {
                log.info("合并重复委派 parentRun={} agent={} task={}", parentRunId, agent.code(), inflight.id());
                if (request.waitForCompletion()) {
                    awaitChild(inflight, parent);
                }
                return AgentTaskResponse.from(inflight);
            }
            return startTask(request, userId, parent, parentRunId, parentSessionId, agent);
        }
    }

    private AgentTaskResponse startTask(
            SubmitAgentTaskRequest request,
            String userId,
            ChatRun parent,
            String parentRunId,
            String parentSessionId,
            AgentRegistryItem agent
    ) {
        AgentTask task = new AgentTask(
                userId, parentRunId, parentSessionId,
                agent.code(), agent.name(), request.message().trim()
        );
        task.setAgentIcon(trim(agent.icon()));
        Instant now = Instant.now();
        String title = "子任务 · " + agent.name();
        sessionService.insertInternal(new ChatSession(
                task.childSessionId(), userId, title, now, now,
                agent.code(), trim(agent.hermesProfile()), agent.name(), "",
                sessionService.resolveWorkspaceSessionId(parentSessionId)
        ));
        ChatRun child = activeRunRegistry.tryBegin(task.childSessionId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "无法启动子智能体轮次"));
        task.setChildRunId(child.runId());
        registry.put(task);
        parent.incrementDelegations(1);
        projectParentTool(parent, task, null);
        child.addEventSink((name, payload) -> onChildEvent(task.id(), name, payload));

        String modelCode = parent.modelCode();
        sseExecutor.execute(() -> {
            try {
                chatStream.streamAnswer(
                        userId, task.childSessionId(), task.message(),
                        modelCode, agent.code(), agent.hermesProfile(),
                        List.of(), null, null, false, false, null, child
                );
            } catch (RuntimeException ex) {
                log.warn("子 ChatRun 失败 task={}: {}", task.id(), ex.toString());
                failIfRunning(task, ex.getMessage());
                ChatRun stillParent = activeRunRegistry.findByRunId(parentRunId).orElse(parent);
                projectParentTool(stillParent, task, task.error());
            }
        });
        if (request.waitForCompletion()) {
            awaitChild(task, parent);
        }
        return AgentTaskResponse.from(task);
    }

    private void awaitChild(AgentTask task, ChatRun parent) {
        long timeoutMs = properties.getClaude().getOrchestrationWaitSeconds() * 1000L;
        boolean done = task.awaitTerminal(timeoutMs, () ->
                parent.isCancelRequested() || !parent.isRunning());
        if (parent.isCancelRequested() || !parent.isRunning()) {
            cancelTask(task);
            return;
        }
        if (done && task.state().terminal()) {
            return;
        }
        task.setError("等待专业智能体完成超时");
        activeRunRegistry.findRunning(task.childSessionId()).ifPresent(ChatRun::requestCancel);
        failIfRunning(task, task.error());
        projectParentTool(parent, task, task.error());
    }

    public AgentTaskResponse get(String id, String userId) {
        AgentTask task = requireOwned(id, userId);
        return AgentTaskResponse.from(task);
    }

    public AgentTaskResponse cancel(String id, String userId) {
        AgentTask task = requireOwned(id, userId);
        cancelTask(task);
        return AgentTaskResponse.from(task);
    }

    public List<AgentTaskResponse> list(String parentRunId, String parentSessionId, String userId) {
        List<AgentTask> tasks = !isBlank(parentRunId)
                ? registry.listByParentRun(parentRunId.trim())
                : registry.listByParentSession(parentSessionId);
        return tasks.stream()
                .filter(t -> userId.equals(t.userId()))
                .map(AgentTaskResponse::from)
                .toList();
    }

    public void cancelByParentRun(String parentRunId) {
        for (AgentTask task : registry.listByParentRun(parentRunId)) {
            cancelTask(task);
        }
    }

    private void cancelTask(AgentTask task) {
        if (task.state().terminal()) {
            return;
        }
        task.transition(AgentTaskState.CANCELED);
        activeRunRegistry.findRunning(task.childSessionId()).ifPresent(ChatRun::requestCancel);
        activeRunRegistry.findByRunId(task.parentRunId()).ifPresent(p -> projectParentTool(p, task, "已取消"));
    }

    private AgentTask requireOwned(String id, String userId) {
        AgentTask task = registry.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!userId.equals(task.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该任务");
        }
        return task;
    }

    private void onChildEvent(String taskId, String name, Map<String, Object> payload) {
        AgentTask task = registry.find(taskId).orElse(null);
        if (task == null) {
            return;
        }
        ChatRun parent = activeRunRegistry.findByRunId(task.parentRunId()).orElse(null);
        if ("started".equals(name)) {
            task.transition(AgentTaskState.WORKING);
            if (parent != null) {
                projectParentTool(parent, task, null);
            }
            return;
        }
        if ("tool_call".equals(name) && parent != null && payload != null) {
            LinkedHashMap<String, Object> child = new LinkedHashMap<>(payload);
            child.put("parentId", task.id());
            child.put("subagent", true);
            child.putIfAbsent("eventType", "subagent.tool");
            safePublish(parent, "tool_call", child);
            return;
        }
        if ("token".equals(name) && parent != null) {
            Object text = payload == null ? null : payload.get("text");
            if (text != null && !String.valueOf(text).isBlank()) {
                throttleProgress(parent, task, String.valueOf(text));
            }
            return;
        }
        if ("done".equals(name)) {
            ChatRun child = activeRunRegistry.findBySession(task.childSessionId()).orElse(null);
            String artifact = child == null ? "" : child.contentSnapshot();
            task.setArtifact(artifact);
            task.transition(AgentTaskState.COMPLETED);
            if (parent != null) {
                projectParentTool(parent, task, artifact);
            }
            return;
        }
        if ("error".equals(name)) {
            Object msg = payload == null ? null : payload.get("message");
            failIfRunning(task, msg == null ? "子智能体失败" : String.valueOf(msg));
            if (parent != null) {
                projectParentTool(parent, task, task.error());
            }
            return;
        }
        if ("cancelled".equals(name)) {
            task.transition(AgentTaskState.CANCELED);
            if (parent != null) {
                projectParentTool(parent, task, "已取消");
            }
        }
    }

    private final ConcurrentThrottle progressAt = new ConcurrentThrottle();

    private void throttleProgress(ChatRun parent, AgentTask task, String chunk) {
        if (!progressAt.allow(task.id(), 800)) {
            return;
        }
        LinkedHashMap<String, Object> row = baseParentTool(task);
        row.put("status", task.state().crewStatus());
        row.put("progress", clip(chunk, 400));
        row.put("summary", clip(chunk, 200));
        row.put("eventType", "subagent.progress");
        safePublish(parent, "tool_call", row);
    }

    private void failIfRunning(AgentTask task, String message) {
        task.setError(message);
        if (task.state().terminal()) {
            return;
        }
        if (task.state() == AgentTaskState.SUBMITTED) {
            task.transition(AgentTaskState.REJECTED);
        } else {
            task.transition(AgentTaskState.FAILED);
        }
    }

    private void projectParentTool(ChatRun parent, AgentTask task, String result) {
        LinkedHashMap<String, Object> row = baseParentTool(task);
        AgentTaskState st = task.state();
        row.put("status", st.crewStatus());
        if (result != null && !result.isBlank()) {
            row.put("result", clip(result, 4000));
            row.put("summary", clip(result, 200));
        } else if (!task.message().isBlank()) {
            row.put("summary", clip(task.agentName() + "：" + task.message(), 200));
        }
        if (st.terminal()) {
            row.put("endedAt", task.updatedAtMs());
            row.put("eventType", "subagent.complete");
        }
        if (st == AgentTaskState.FAILED || st == AgentTaskState.REJECTED) {
            row.put("error", clip(task.error(), 500));
        }
        safePublish(parent, "tool_call", row);
        if (isDelegationName(TOOL_NAME)) {
            safePublish(parent, "delegation_update", QianXunServiceChatStream.delegationUpdatePayload(row, TOOL_NAME));
        }
    }

    private LinkedHashMap<String, Object> baseParentTool(AgentTask task) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("toolCallId", task.id());
        row.put("toolName", TOOL_NAME);
        row.put("displayName", task.agentName().isBlank() ? "专业智能体" : task.agentName());
        row.put("iconKind", "agent");
        row.put("subagent", true);
        row.put("delegationId", task.id());
        row.put("agentCode", task.agentCode());
        if (!task.agentIcon().isBlank()) {
            row.put("agentIcon", task.agentIcon());
        }
        row.put("a2aState", task.state().wire());
        row.put("childSessionId", task.childSessionId());
        row.put("startedAt", task.createdAtMs());
        row.put("args", "{\"agentCode\":\"" + escape(task.agentCode())
                + "\",\"message\":\"" + escape(clip(task.message(), 500)) + "\"}");
        return row;
    }

    private static void safePublish(ChatRun run, String name, Map<String, Object> data) {
        if (run == null || !run.isRunning()) {
            return;
        }
        try {
            if ("done".equals(name) || "error".equals(name) || "cancelled".equals(name)) {
                run.publishTerminal(name, data);
            } else {
                run.publish(name, data);
            }
        } catch (CancellationException ignored) {
            /* 父轮已停 */
        }
    }

    private static boolean isDelegationName(String name) {
        return name != null && name.toLowerCase().contains("delegate");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class ConcurrentThrottle {
        private final java.util.concurrent.ConcurrentHashMap<String, AtomicLong> last =
                new java.util.concurrent.ConcurrentHashMap<>();

        boolean allow(String key, long minIntervalMs) {
            long now = System.currentTimeMillis();
            AtomicLong holder = last.computeIfAbsent(key, k -> new AtomicLong(0));
            long prev = holder.get();
            if (now - prev < minIntervalMs) {
                return false;
            }
            return holder.compareAndSet(prev, now);
        }
    }
}
