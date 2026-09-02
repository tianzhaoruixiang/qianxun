package com.qianxun.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.config.QianxunProperties;
import com.qianxun.service.ChatDashboardTurn;
import com.qianxun.service.ChatSkillInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 智能体聊天走 Dashboard {@code ws://…:9119/api/ws}（JSON-RPC：session.create /
 * prompt.submit / slash.exec），而不是网关 {@code POST /v1/chat/completions}。
 */
@Component
public class HermesDashboardChatClient {

    private static final Logger log = LoggerFactory.getLogger(HermesDashboardChatClient.class);
    private static final String DOC_HINT =
            "【工作区规则】每个用户可见的多轮会话有独立 cwd，子智能体与父会话共用该目录。"
                    + "文件/终端操作只能使用该 cwd 下的相对路径；"
                    + "不要使用绝对路径访问其它目录，不要 ls/读取父目录（..）或同级其它 qx 用户目录。"
                    + "只有当用户确实要求访问该目录之外的路径时，才简要说明无法访问并继续处理其余部分；"
                    + "其余情况正常回答，不要主动提及本规则，也不要输出与隔离有关的状态语。"
                    + "若生成 xlsx/md/doc/docx，用 write_file 写入当前 cwd（普通文件名如 report.xlsx）。"
                    + "不要编造 /QianXunService/data/files/public/ 链接，也不要把 Docker 内部主机名发给用户。"
                    + "平台会自动入库并追加可点击链接。";

    /** 完整规则：仅在 {@code session.create} 作为 system 消息注入一次。 */
    static String workspaceFence(String cwd) {
        String c = cwd == null ? "" : cwd.trim();
        if (c.isBlank()) {
            return DOC_HINT;
        }
        return DOC_HINT + " 唯一允许的工作目录是：" + c + "。";
    }

    /**
     * 每轮前置的简短提醒：只重申 cwd，不重复整段规则。
     * 逐轮重复「禁止/拒绝」类措辞会让模型把隔离当成话题，对正常问题也答隔离状态。
     */
    static String turnFence(String cwd) {
        String c = cwd == null ? "" : cwd.trim();
        if (c.isBlank()) {
            return "";
        }
        return "【当前工作目录】" + c + "（文件/终端操作请使用此目录下的相对路径）";
    }

    private final HermesAgentClient hermes;
    private final ObjectMapper objectMapper;
    private final QianxunProperties properties;
    private final ConcurrentHashMap<String, String> liveSessionIds = new ConcurrentHashMap<>();

    public HermesDashboardChatClient(
            HermesAgentClient hermes,
            ObjectMapper objectMapper,
            QianxunProperties properties
    ) {
        this.hermes = hermes;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public OpenAiCompatibleStreamClient.StreamCompletionMeta streamTurn(
            String cacheKey,
            String workspaceOwnerId,
            String profile,
            ChatDashboardTurn.Plan plan,
            OpenAiCompatibleStreamClient.StreamTokenConsumer consumer,
            OpenAiCompatibleStreamClient.ToolCallListener toolListener,
            OpenAiCompatibleStreamClient.UsageListener usageListener
    ) throws Exception {
        return streamTurn(
                cacheKey, workspaceOwnerId, profile, plan, false,
                consumer, toolListener, usageListener, null, null);
    }

    /**
     * @param awaitGoalContinuations 会话有活跃 /goal 时为 true：message.complete 后还可能
     *                               跑 judge + Ralph 续轮，需更长 settle 窗口。
     */
    public OpenAiCompatibleStreamClient.StreamCompletionMeta streamTurn(
            String cacheKey,
            String workspaceOwnerId,
            String profile,
            ChatDashboardTurn.Plan plan,
            boolean awaitGoalContinuations,
            OpenAiCompatibleStreamClient.StreamTokenConsumer consumer,
            OpenAiCompatibleStreamClient.ToolCallListener toolListener,
            OpenAiCompatibleStreamClient.UsageListener usageListener
    ) throws Exception {
        return streamTurn(
                cacheKey, workspaceOwnerId, profile, plan, awaitGoalContinuations,
                consumer, toolListener, usageListener, null, null);
    }

    /**
     * @param cacheKey         千寻会话 ID：决定复用哪个上游 Dashboard session
     * @param workspaceOwnerId 千寻用户 ID：决定用户数据根目录
     */
    public OpenAiCompatibleStreamClient.StreamCompletionMeta streamTurn(
            String cacheKey,
            String workspaceOwnerId,
            String profile,
            ChatDashboardTurn.Plan plan,
            boolean awaitGoalContinuations,
            OpenAiCompatibleStreamClient.StreamTokenConsumer consumer,
            OpenAiCompatibleStreamClient.ToolCallListener toolListener,
            OpenAiCompatibleStreamClient.UsageListener usageListener,
            BooleanSupplier cancelled,
            Consumer<Runnable> registerCancel
    ) throws Exception {
        if (!hermes.isConfigured()) {
            throw new IllegalStateException("未启用或未配置智能体管理面");
        }
        throwIfCancelled(cancelled);
        try {
            return streamTurnOnce(
                    cacheKey, workspaceOwnerId, profile, plan, awaitGoalContinuations, false,
                    consumer, toolListener, usageListener, cancelled, registerCancel);
        } catch (CancellationException cancelledEx) {
            throw cancelledEx;
        } catch (Exception first) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                throw new CancellationException("run cancelled");
            }
            // Hermes 重启 / 会话被清后，内存里的上游 session_id 会失效
            if (!isSessionMissing(first)) {
                throw first;
            }
            liveSessionIds.remove(cacheKey(cacheKey, profile));
            log.info("上游会话已失效，重建后重试 sessionKey={}", truncate(cacheKey, 36));
            return streamTurnOnce(
                    cacheKey, workspaceOwnerId, profile, plan, awaitGoalContinuations, true,
                    consumer, toolListener, usageListener, cancelled, registerCancel);
        }
    }

    private static void throwIfCancelled(BooleanSupplier cancelled) {
        if (cancelled != null && cancelled.getAsBoolean()) {
            throw new CancellationException("run cancelled");
        }
    }

    private OpenAiCompatibleStreamClient.StreamCompletionMeta streamTurnOnce(
            String cacheKey,
            String workspaceOwnerId,
            String profile,
            ChatDashboardTurn.Plan plan,
            boolean awaitGoalContinuations,
            boolean forceNewSession,
            OpenAiCompatibleStreamClient.StreamTokenConsumer consumer,
            OpenAiCompatibleStreamClient.ToolCallListener toolListener,
            OpenAiCompatibleStreamClient.UsageListener usageListener,
            BooleanSupplier cancelled,
            Consumer<Runnable> registerCancel
    ) throws Exception {
        throwIfCancelled(cancelled);
        String ticket = hermes.mintWsTicket();
        URI uri = URI.create(websocketOrigin(hermes.origin()) + "/api/ws?ticket="
                + URLEncoder.encode(ticket, StandardCharsets.UTF_8));
        long settleMs = awaitGoalContinuations ? GOAL_TURN_SETTLE_GRACE_MS : DEFAULT_TURN_SETTLE_GRACE_MS;
        RpcSocket socket = new RpcSocket(objectMapper, consumer, toolListener, usageListener, settleMs);
        WebSocket ws = hermes.httpClient().newWebSocketBuilder()
                .buildAsync(uri, socket)
                .get(20, TimeUnit.SECONDS);
        if (registerCancel != null) {
            registerCancel.accept(() -> {
                socket.abortTurn();
                try {
                    ws.abort();
                } catch (Exception ignored) {
                    /* already closed */
                }
            });
        }
        throwIfCancelled(cancelled);
        Duration wait = streamWait();
        ScheduledFuture<?> wsPing = WS_PING_SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                ws.sendPing(ByteBuffer.allocate(0));
            } catch (Exception ignored) {
                /* 连接已关或发送失败，下一轮会自然结束 */
            }
        }, WS_PING_INTERVAL_SECONDS, WS_PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
        try {
            socket.awaitReady(Duration.ofSeconds(8));
            String workspaceCwd = ensureUserWorkspace(workspaceOwnerId, profile);
            String sid = ensureSession(
                    socket, ws, cacheKey, profile, plan.seedHistory(), workspaceCwd, wait, forceNewSession);
            socket.setSessionId(sid);
            preferVerboseToolEvents(socket, ws, sid, wait);
            if (plan.hasSlash()) {
                JsonNode slash = execSlash(socket, ws, sid, plan.slashCommand(), wait);
                String kind = slash.path("type").asText("");
                String output = slash.path("output").asText("");
                String notice = slash.path("notice").asText("");
                String sendText = slash.path("message").asText("");
                if (!notice.isBlank() && consumer != null) {
                    consumer.onToken(notice.endsWith("\n") ? notice : notice + "\n");
                }
                // send=/goal 等；skill=上游已展开 SKILL.md，须 prompt.submit 该 message
                // /agents 等只读斜杠：type 多为 output，走下方 output 分支
                boolean submitExpanded = "send".equals(kind) || "skill".equals(kind);
                if (submitExpanded || plan.expectSendThenPrompt()) {
                    String text = !sendText.isBlank() ? sendText : plan.promptText();
                    if (text == null || text.isBlank()) {
                        text = sendText;
                    }
                    submitPrompt(socket, ws, sid, text, workspaceCwd, wait);
                } else {
                    if (!output.isBlank() && consumer != null) {
                        consumer.onToken(output);
                    }
                    socket.completeTurnIfIdle();
                }
            } else {
                submitPrompt(socket, ws, sid, plan.promptText(), workspaceCwd, wait);
            }
            OpenAiCompatibleStreamClient.StreamCompletionMeta meta = socket.awaitTurn(wait);
            OpenAiCompatibleStreamClient.TokenUsage snap = fetchSessionUsage(socket, ws, sid);
            if (snap == null) {
                return meta;
            }
            if (usageListener != null) {
                usageListener.onUsage(snap);
            }
            return new OpenAiCompatibleStreamClient.StreamCompletionMeta(meta.sawDone(), meta.finishReason(), snap);
        } catch (Exception ex) {
            liveSessionIds.remove(cacheKey(cacheKey, profile));
            throw ex;
        } finally {
            wsPing.cancel(false);
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").orTimeout(3, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                try {
                    ws.abort();
                } catch (Exception ignored2) {
                    /* already closed */
                }
            }
        }
    }

    public static String cacheKey(String qianxunSessionId, String profile) {
        String sid = qianxunSessionId == null ? "" : qianxunSessionId.trim();
        String p = profile == null ? "" : profile.trim();
        // v4：每会话独立 cwd（task 子会话复用父会话工作区）+ 每轮工作区围栏
        return "v4\0" + sid + "\0" + p;
    }

    static String websocketOrigin(String httpOrigin) {
        String s = httpOrigin == null ? "" : httpOrigin.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.regionMatches(true, 0, "https://", 0, 8)) {
            return "wss://" + s.substring(8);
        }
        if (s.regionMatches(true, 0, "http://", 0, 7)) {
            return "ws://" + s.substring(7);
        }
        return s;
    }

    private Duration streamWait() {
        int sec = properties.getLlm().getStreamTimeoutSeconds();
        if (sec > 0) {
            return Duration.ofSeconds(sec);
        }
        return Duration.ofMinutes(30);
    }

    /**
     * 为千寻用户在 Hermes profile 下创建独立 cwd，避免多用户共用同一智能体时工作区串扰。
     * 同一用户的多个会话共享该目录；若 profile 根目录有 {@code AGENTS.md}，同步到该 cwd，
     * 供 Hermes 在启动时加载项目上下文。
     */
    String ensureUserWorkspace(String qianxunUserId, String profile) {
        String cwd = HermesWorkspaceSandbox.resolve(profile, qianxunUserId);
        HermesAgentClient.ManagedMkdirResult mkdir = hermes.ensureManagedDirectory(qianxunUserId, profile, cwd);
        if (!mkdir.ok()) {
            throw new IllegalStateException("无法创建用户工作区: " + mkdir.message());
        }
        seedAgentsMdIntoWorkspace(qianxunUserId, profile, cwd);
        return cwd;
    }

    /**
     * 若 profile 根目录有 {@code AGENTS.md}，同步到用户工作区 cwd。
     */
    private void seedAgentsMdIntoWorkspace(String qianxunUserId, String profile, String cwd) {
        String c = cwd == null ? "" : cwd.trim();
        if (c.isBlank()) {
            return;
        }
        try {
            String home = HermesWorkspaceSandbox.profileHome(qianxunUserId, profile);
            String src = home + "/AGENTS.md";
            HermesAgentClient.DownloadedFile file = hermes.downloadManagedFile(qianxunUserId, profile, src, false);
            if (!file.ok() || file.bytes() == null || file.bytes().length == 0) {
                return;
            }
            HermesAgentClient.ManagedWriteResult w = hermes.writeManagedFile(qianxunUserId, c + "/AGENTS.md", file.bytes());
            if (!w.ok()) {
                log.debug("会话工作区写入 AGENTS.md 跳过: {}", w.message());
            }
        } catch (Exception ex) {
            log.debug("会话工作区种子 AGENTS.md 失败（忽略）: {}", ex.toString());
        }
    }

    private String ensureSession(
            RpcSocket socket,
            WebSocket ws,
            String cacheKey,
            String profile,
            List<Map<String, String>> seedHistory,
            String workspaceCwd,
            Duration wait,
            boolean forceNew
    ) throws Exception {
        String key = cacheKey(cacheKey, profile);
        if (forceNew) {
            liveSessionIds.remove(key);
        } else {
            String cached = liveSessionIds.get(key);
            if (cached != null && !cached.isBlank()) {
                return cached;
            }
        }
        String sid = createSession(socket, ws, profile, seedHistory, workspaceCwd, wait);
        liveSessionIds.put(key, sid);
        return sid;
    }

    /** Hermes 重启或会话被清后，缓存的 session_id 会报 not found / unknown session。 */
    static boolean isSessionMissing(Throwable err) {
        for (Throwable t = err; t != null; t = t.getCause()) {
            if (isSessionMissingMessage(t.getMessage())) {
                return true;
            }
        }
        return false;
    }

    static boolean isSessionMissingMessage(String msg) {
        if (msg == null || msg.isBlank()) {
            return false;
        }
        String lower = msg.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("session not found")
                || lower.contains("unknown session")
                || lower.contains("no such session")
                || lower.contains("session_id not found")
                || lower.contains("invalid session");
    }

    private String createSession(
            RpcSocket socket,
            WebSocket ws,
            String profile,
            List<Map<String, String>> seedHistory,
            String workspaceCwd,
            Duration wait
    ) throws Exception {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("source", "cli");
        params.put("close_on_disconnect", false);
        String p = hermes.normalizeProfileName(profile);
        if (!p.isBlank() && !"default".equalsIgnoreCase(p)) {
            params.put("profile", p);
        }
        String cwd = workspaceCwd == null ? "" : workspaceCwd.trim();
        if (!cwd.isBlank()) {
            params.put("cwd", cwd);
        }
        List<Map<String, String>> seed = withDocHint(seedHistory, cwd);
        if (!seed.isEmpty()) {
            params.put("messages", seed);
        }
        JsonNode result = rpc(socket, ws, "session.create", params, wait);
        String sid = result.path("session_id").asText("");
        if (sid.isBlank()) {
            throw new IllegalStateException("上游未返回会话编号");
        }
        return sid;
    }

    private static List<Map<String, String>> withDocHint(
            List<Map<String, String>> seedHistory,
            String workspaceCwd
    ) {
        List<Map<String, String>> out = new java.util.ArrayList<>();
        out.add(Map.of("role", "system", "content", workspaceFence(workspaceCwd)));
        if (seedHistory != null) {
            out.addAll(seedHistory);
        }
        return List.copyOf(out);
    }

    private void submitPrompt(
            RpcSocket socket,
            WebSocket ws,
            String sid,
            String text,
            String workspaceCwd,
            Duration wait
    ) throws Exception {
        String body = text == null ? "" : text;
        if (body.isBlank()) {
            throw new IllegalStateException("本轮没有可发送的用户内容");
        }
        String fence = turnFence(workspaceCwd);
        String fenced = fence.isBlank() ? body : fence + "\n\n" + body;
        JsonNode accepted = rpc(socket, ws, "prompt.submit", Map.of(
                "session_id", sid,
                "text", fenced
        ), wait);
        String status = accepted.path("status").asText("");
        if (!status.isBlank() && !"streaming".equals(status)) {
            log.debug("prompt.submit status={}", status);
        }
    }

    /**
     * 与官方 Web/TUI 一致：先 {@code slash.exec}；技能斜杠会被拒并要求
     * {@code command.dispatch}（{@code /goal} 则在 PENDING_INPUT 内由 slash.exec 内转发）。
     */
    private JsonNode execSlash(
            RpcSocket socket,
            WebSocket ws,
            String sid,
            String command,
            Duration wait
    ) throws Exception {
        String cmd = command == null ? "" : command.trim();
        try {
            return rpc(socket, ws, "slash.exec", Map.of(
                    "session_id", sid,
                    "command", cmd
            ), wait);
        } catch (IllegalStateException ex) {
            if (!needsCommandDispatchFallback(ex.getMessage())) {
                throw ex;
            }
            log.debug("slash.exec → command.dispatch for skill/bundle: {}", truncate(cmd, 120));
            try {
                return dispatchSlash(socket, ws, sid, cmd, wait);
            } catch (IllegalStateException dispatchEx) {
                if (!canFallbackSkillPrompt(dispatchEx.getMessage(), cmd)) {
                    throw dispatchEx;
                }
                log.info("slash/dispatch unknown, prompt-submit skill 「{}」", parseSlashCommand(cmd).name());
                return skillPromptFallback(cmd);
            }
        }
    }

    private JsonNode dispatchSlash(
            RpcSocket socket,
            WebSocket ws,
            String sid,
            String command,
            Duration wait
    ) throws Exception {
        SlashParts parts = parseSlashCommand(command);
        if (parts.name().isBlank()) {
            throw new IllegalStateException("空的斜杠指令");
        }
        return rpc(socket, ws, "command.dispatch", Map.of(
                "session_id", sid,
                "name", parts.name(),
                "arg", parts.arg()
        ), wait);
    }

    static boolean needsCommandDispatchFallback(String err) {
        if (err == null || err.isBlank() || isSessionMissingMessage(err)) {
            return false;
        }
        String e = err.toLowerCase(java.util.Locale.ROOT);
        if (e.contains("unauthenticated") || e.contains("ticket")) {
            return false;
        }
        if (e.contains("timeout") || e.contains("timed out")) {
            return false;
        }
        // 官方 TUI：slash.exec 失败后改走 command.dispatch。
        // 技能斜杠常见文案是 "use command.dispatch"；目录未进 slash 目录时则是 Unknown command。
        return e.contains("use command.dispatch") || e.contains("unknown command");
    }

    static boolean looksLikeUnknownSlash(String err) {
        if (err == null || err.isBlank()) {
            return false;
        }
        String e = err.toLowerCase(java.util.Locale.ROOT);
        return e.contains("unknown command")
                || e.contains("not a quick/plugin");
    }

    /** /goal、/agents 是内置斜杠，失败时不要改写成技能提示词。 */
    static boolean looksLikeSkillSlash(String command) {
        SlashParts parts = parseSlashCommand(command);
        String name = parts.name() == null ? "" : parts.name().trim().toLowerCase(java.util.Locale.ROOT);
        if (name.isBlank()) {
            return false;
        }
        return !Set.of("goal", "agents", "tasks", "task").contains(name);
    }

    static boolean canFallbackSkillPrompt(String err, String command) {
        return looksLikeUnknownSlash(err) && looksLikeSkillSlash(command);
    }

    JsonNode skillPromptFallback(String command) {
        SlashParts parts = parseSlashCommand(command);
        String message = ChatSkillInvocation.prefixUserTask(parts.name(), parts.arg());
        com.fasterxml.jackson.databind.node.ObjectNode n = objectMapper.createObjectNode();
        n.put("type", "skill");
        n.put("message", message);
        return n;
    }

    /** 解析 {@code /name arg…} 为 command.dispatch 的 name/arg（name 不含前导 /）。 */
    static SlashParts parseSlashCommand(String command) {
        String raw = command == null ? "" : command.trim();
        if (raw.startsWith("/")) {
            raw = raw.substring(1);
        }
        int sp = -1;
        for (int i = 0; i < raw.length(); i++) {
            if (Character.isWhitespace(raw.charAt(i))) {
                sp = i;
                break;
            }
        }
        String name = sp < 0 ? raw : raw.substring(0, sp);
        String arg = sp < 0 ? "" : raw.substring(sp).trim();
        return new SlashParts(name, arg);
    }

    record SlashParts(String name, String arg) {}

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    /**
     * 开启会话 {@code tool_progress=verbose}，以便 WS 附带 {@code args_text}/{@code result_text}。
     * 失败不影响主流程；非 verbose 时 {@code tool.complete} 仍有完整 {@code args}/{@code result}。
     */
    private void preferVerboseToolEvents(RpcSocket socket, WebSocket ws, String sid, Duration wait) {
        try {
            rpc(socket, ws, "config.set", Map.of(
                    "session_id", sid,
                    "key", "verbose",
                    "value", "verbose"
            ), Duration.ofSeconds(Math.min(8, Math.max(3, wait.toSeconds()))));
        } catch (Exception e) {
            log.debug("enable verbose tool events skipped: {}", e.toString());
        }
    }

    private JsonNode rpc(
            RpcSocket socket,
            WebSocket ws,
            String method,
            Map<String, Object> params,
            Duration wait
    ) throws Exception {
        String id = socket.nextId();
        CompletableFuture<JsonNode> fut = socket.expect(id);
        String json = HermesDashboardRpc.request(objectMapper, id, method, params);
        ws.sendText(json, true).join();
        JsonNode result = fut.get(wait.toSeconds(), TimeUnit.SECONDS);
        String err = HermesDashboardRpc.rpcError(result);
        if (!err.isBlank()) {
            throw new IllegalStateException(userFacing(method, err));
        }
        return result.path("result");
    }

    private static String userFacing(String method, String err) {
        if (err == null || err.isBlank()) {
            return "上游对话失败";
        }
        if (err.toLowerCase().contains("unauthenticated") || err.contains("ticket")) {
            return "上游对话通道鉴权失败，请重试";
        }
        if ("slash.exec".equals(method) || "command.dispatch".equals(method)) {
            return "斜杠指令失败: " + err;
        }
        if ("session.create".equals(method)) {
            return "无法创建上游会话: " + err;
        }
        if ("session.usage".equals(method)) {
            return "无法读取上游用量: " + err;
        }
        return "上游对话失败: " + err;
    }

    private OpenAiCompatibleStreamClient.TokenUsage fetchSessionUsage(RpcSocket socket, WebSocket ws, String sid) {
        if (sid == null || sid.isBlank()) {
            return null;
        }
        try {
            JsonNode result = rpc(socket, ws, "session.usage", Map.of("session_id", sid), Duration.ofSeconds(5));
            return HermesDashboardRpc.toUsage(result);
        } catch (Exception e) {
            log.debug("session.usage skipped: {}", e.toString());
            return null;
        }
    }

    /**
     * Hermes 在 {@code message.complete} 之后可能立刻再开一轮（/goal continuation、
     * 后台 {@code delegate_task} 完成后的通知 drain、queued prompt）。客户端必须等
     * settle 窗口；若收到 {@code message.start} / {@code session.info.running=true}
     * 则继续等下一帧 complete。
     * <p>
     * 普通轮次：短窗口（覆盖 finally→偶发 follow-up）。
     * 有活跃 /goal：长窗口（覆盖 goal judge 的 LLM 调用，可达数十秒）。
     * 已派工后台子智能体：更长窗口（子任务常需数十秒到数分钟；过早关 WS 会把续轮
     * 事件丢到 DropTransport，用户永远收不到汇合回复）。
     */
    static final long DEFAULT_TURN_SETTLE_GRACE_MS = 800L;
    static final long GOAL_TURN_SETTLE_GRACE_MS = 120_000L;
    /** 后台 delegate_task 完成后，tui_gateway 通知 poller 会再开一轮；须在此之前保持 WS。 */
    static final long BACKGROUND_DELEGATION_SETTLE_GRACE_MS = 600_000L;
    /** 等待子智能体期间，每隔一段时间推送一次「仍在执行」进度，避免前端假死感。 */
    static final long BACKGROUND_DELEGATION_HEARTBEAT_MS = 15_000L;
    static final long IDLE_AFTER_SESSION_INFO_MS = 2_000L;

    private static final ScheduledExecutorService TURN_SETTLE_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "qianxun-hermes-turn-settle");
                t.setDaemon(true);
                return t;
            });

    /** 工具长跑时上游可能无文本帧；协议层 ping 防止中间件掐断 Dashboard WS。 */
    private static final long WS_PING_INTERVAL_SECONDS = 20;
    private static final ScheduledExecutorService WS_PING_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "qianxun-hermes-ws-ping");
                t.setDaemon(true);
                return t;
            });

    static final class RpcSocket implements WebSocket.Listener {
        private final ObjectMapper mapper;
        private final OpenAiCompatibleStreamClient.StreamTokenConsumer consumer;
        private final OpenAiCompatibleStreamClient.ToolCallListener toolListener;
        private final OpenAiCompatibleStreamClient.UsageListener usageListener;
        private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
        private final CompletableFuture<Void> ready = new CompletableFuture<>();
        private final CompletableFuture<OpenAiCompatibleStreamClient.StreamCompletionMeta> turnDone =
                new CompletableFuture<>();
        private final StringBuilder fragments = new StringBuilder();
        private final AtomicInteger seq = new AtomicInteger();
        private final AtomicReference<String> sessionId = new AtomicReference<>("");
        private final AtomicReference<OpenAiCompatibleStreamClient.TokenUsage> usage = new AtomicReference<>();
        private final long settleGraceMs;
        private final Object settleLock = new Object();
        private volatile boolean sawComplete;
        /**
         * 本轮已看到后台 {@code delegate_task}（status=dispatched）。父轮 message.complete
         * 后须长等通知 drain 的 message.start；drain 轮一旦开始（message.start）即清掉，
         * 若 drain 轮内再次派工会重新置位。
         */
        private volatile boolean expectBackgroundFollowUp;
        private volatile ScheduledFuture<?> settleFuture;
        private volatile ScheduledFuture<?> heartbeatFuture;
        private volatile OpenAiCompatibleStreamClient.StreamCompletionMeta pendingMeta;
        /** 处于 awaiting 的后台 delegate_task tool_id，用于进度心跳与汇入后收尾。 */
        private final Set<String> awaitingDelegateIds = ConcurrentHashMap.newKeySet();
        private final AtomicLong backgroundWaitStartedAt = new AtomicLong(0L);
        private final AtomicInteger backgroundHeartbeatSeq = new AtomicInteger();
        private final AtomicInteger liveSubagentCount = new AtomicInteger();
        private final AtomicReference<String> lastSubagentProgress = new AtomicReference<>("");

        RpcSocket(
                ObjectMapper mapper,
                OpenAiCompatibleStreamClient.StreamTokenConsumer consumer,
                OpenAiCompatibleStreamClient.ToolCallListener toolListener,
                OpenAiCompatibleStreamClient.UsageListener usageListener
        ) {
            this(mapper, consumer, toolListener, usageListener, DEFAULT_TURN_SETTLE_GRACE_MS);
        }

        RpcSocket(
                ObjectMapper mapper,
                OpenAiCompatibleStreamClient.StreamTokenConsumer consumer,
                OpenAiCompatibleStreamClient.ToolCallListener toolListener,
                OpenAiCompatibleStreamClient.UsageListener usageListener,
                long settleGraceMs
        ) {
            this.mapper = mapper;
            this.consumer = consumer;
            this.toolListener = toolListener;
            this.usageListener = usageListener;
            this.settleGraceMs = Math.max(50L, settleGraceMs);
        }

        String nextId() {
            return "q" + seq.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
        }

        CompletableFuture<JsonNode> expect(String id) {
            CompletableFuture<JsonNode> fut = new CompletableFuture<>();
            pending.put(id, fut);
            return fut;
        }

        void setSessionId(String sid) {
            sessionId.set(sid == null ? "" : sid);
        }

        void completeTurnIfIdle() {
            cancelSettle();
            cancelHeartbeat();
            if (!turnDone.isDone()) {
                sawComplete = true;
                turnDone.complete(new OpenAiCompatibleStreamClient.StreamCompletionMeta(
                        true, "stop", usage.get()));
            }
        }

        void abortTurn() {
            cancelSettle();
            cancelHeartbeat();
            if (!turnDone.isDone()) {
                turnDone.completeExceptionally(new CancellationException("run cancelled"));
            }
        }

        void awaitReady(Duration wait) {
            try {
                ready.get(Math.max(1, wait.toSeconds()), TimeUnit.SECONDS);
            } catch (Exception ignored) {
                /* 未等到 gateway.ready 也可发 RPC */
            }
        }

        OpenAiCompatibleStreamClient.StreamCompletionMeta awaitTurn(Duration wait) throws Exception {
            try {
                return turnDone.get(wait.toSeconds(), TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException ex) {
                throw new IllegalStateException("上游对话超时", ex);
            } catch (java.util.concurrent.ExecutionException ex) {
                Throwable c = ex.getCause() == null ? ex : ex.getCause();
                if (c instanceof Exception e) {
                    throw e;
                }
                throw new IllegalStateException(c.getMessage() == null ? "上游对话失败" : c.getMessage(), c);
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            fragments.append(data);
            if (last) {
                String raw = fragments.toString();
                fragments.setLength(0);
                handleText(raw);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            cancelSettle();
            cancelHeartbeat();
            if (!turnDone.isDone()) {
                if (sawComplete) {
                    turnDone.complete(pendingMeta != null
                            ? pendingMeta
                            : new OpenAiCompatibleStreamClient.StreamCompletionMeta(
                                    true, "stop", usage.get()));
                } else {
                    String why = reason == null || reason.isBlank() ? ("code " + statusCode) : reason;
                    turnDone.completeExceptionally(new IllegalStateException("上游对话连接已关闭（" + why + "）"));
                }
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            cancelSettle();
            cancelHeartbeat();
            if (!turnDone.isDone()) {
                turnDone.completeExceptionally(new IllegalStateException(
                        "上游对话连接出错: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()),
                        error));
            }
        }

        private void handleText(String raw) {
            if (raw == null || raw.isBlank()) {
                return;
            }
            for (String line : raw.split("\n")) {
                String json = line.trim();
                if (json.isEmpty()) {
                    continue;
                }
                try {
                    handleNode(mapper.readTree(json));
                } catch (Exception ex) {
                    log.debug("忽略无法解析的上游帧: {}", ex.toString());
                }
            }
        }

        void handleNode(JsonNode root) {
            String type = HermesDashboardRpc.eventType(root);
            if (!type.isBlank()) {
                onEvent(type, HermesDashboardRpc.eventSessionId(root), HermesDashboardRpc.eventPayload(root));
                return;
            }
            if (!HermesDashboardRpc.isRpcResult(root)) {
                return;
            }
            String id = HermesDashboardRpc.rpcId(root);
            CompletableFuture<JsonNode> fut = id.isBlank() ? null : pending.remove(id);
            if (fut != null) {
                fut.complete(root);
            }
        }

        private void onEvent(String type, String sid, JsonNode payload) {
            if ("gateway.ready".equals(type)) {
                ready.complete(null);
                return;
            }
            String want = sessionId.get();
            if (!want.isBlank() && !sid.isBlank() && !want.equals(sid)) {
                return;
            }
            if ("message.start".equals(type)) {
                // /goal 续轮、通知 drain、queued prompt：上一帧 complete 不是整轮结束
                cancelSettle();
                boolean wasAwaiting = expectBackgroundFollowUp || !awaitingDelegateIds.isEmpty();
                // drain 轮已开始：清掉「等后台汇入」标记；本轮若再派工会重新置位
                expectBackgroundFollowUp = false;
                cancelHeartbeat();
                if (wasAwaiting) {
                    finishAwaitingDelegates("子智能体结果已汇入，正在汇总…");
                }
                return;
            }
            if ("session.info".equals(type)) {
                publishDashboardUsage(payload);
                if (payload != null && payload.path("running").asBoolean(false)) {
                    cancelSettle();
                } else if (sawComplete && pendingMeta != null && !turnDone.isDone()) {
                    // 回合 finally 已 idle；短等 message.start（goal / 后台汇入续轮）
                    long idleGrace = expectBackgroundFollowUp
                            ? BACKGROUND_DELEGATION_SETTLE_GRACE_MS
                            : IDLE_AFTER_SESSION_INFO_MS;
                    scheduleSettle(pendingMeta, idleGrace);
                }
                return;
            }
            if ("message.delta".equals(type) || "message.interim".equals(type)) {
                String text = HermesDashboardRpc.deltaText(payload);
                if (!text.isEmpty() && consumer != null) {
                    consumer.onToken(text);
                }
                return;
            }
            if (type != null && type.startsWith("subagent.")) {
                handleSubagentEvent(type, payload);
                return;
            }
            if ("status.update".equals(type) && payload != null) {
                String kind = payload.path("kind").asText("");
                String text = payload.path("text").asText("");
                if ("goal".equals(kind)) {
                    if (!text.isBlank() && consumer != null) {
                        consumer.onToken(text.endsWith("\n") ? "\n" + text : "\n" + text + "\n");
                    }
                    if (looksLikeGoalContinuation(text)) {
                        // judge 说继续：取消 settle，等 message.start
                        cancelSettle();
                    } else if (looksLikeGoalTerminal(text) && pendingMeta != null) {
                        scheduleSettle(pendingMeta, IDLE_AFTER_SESSION_INFO_MS);
                    }
                    return;
                }
                // 后台子任务 / terminal notify 完成：tui_gateway 会先推 process 再开 drain 轮
                if ("process".equals(kind)) {
                    boolean bg = expectBackgroundFollowUp || looksLikeBackgroundProcessNotice(text);
                    if (bg) {
                        expectBackgroundFollowUp = true;
                        cancelSettle();
                        if (!text.isBlank()) {
                            lastSubagentProgress.set(text.trim());
                            pushAwaitingProgress(text.trim());
                        }
                    } else if (!text.isBlank() && consumer != null) {
                        consumer.onToken(text.endsWith("\n") ? "\n" + text : "\n" + text + "\n");
                    }
                    return;
                }
            }
            OpenAiCompatibleStreamClient.ToolCallEvent tc = HermesDashboardRpc.toToolEvent(type, payload);
            if (tc != null) {
                if (looksLikeBackgroundDelegation(tc.functionName(), payload)) {
                    expectBackgroundFollowUp = true;
                    cancelSettle();
                    tc = HermesDashboardRpc.asAwaitingBackground(tc, payload);
                    if (tc.toolCallId() != null && !tc.toolCallId().isBlank()) {
                        awaitingDelegateIds.add(tc.toolCallId());
                    }
                    markBackgroundWaitStarted();
                    ensureHeartbeat();
                }
                if (toolListener != null) {
                    toolListener.onToolCall(tc);
                }
                return;
            }
            if ("message.complete".equals(type)) {
                sawComplete = true;
                String status = payload == null ? "" : payload.path("status").asText("");
                publishDashboardUsage(payload);
                if ("error".equals(status)) {
                    cancelSettle();
                    cancelHeartbeat();
                    String err = payload.path("error").asText("");
                    if (err.isBlank()) {
                        err = HermesDashboardRpc.deltaText(payload);
                    }
                    turnDone.completeExceptionally(new IllegalStateException(
                            err.isBlank() ? "上游对话失败" : err));
                    return;
                }
                OpenAiCompatibleStreamClient.StreamCompletionMeta meta =
                        new OpenAiCompatibleStreamClient.StreamCompletionMeta(true, "stop", usage.get());
                // interrupted：用户/系统打断，立即结束，不走 goal / 后台续轮窗口
                if ("interrupted".equals(status)) {
                    cancelSettle();
                    cancelHeartbeat();
                    finishAwaitingDelegates("任务已中断");
                    turnDone.complete(meta);
                    return;
                }
                // complete：可能还有 /goal continuation 或后台 delegate 汇入续轮
                long grace = expectBackgroundFollowUp
                        ? Math.max(settleGraceMs, BACKGROUND_DELEGATION_SETTLE_GRACE_MS)
                        : settleGraceMs;
                if (expectBackgroundFollowUp) {
                    markBackgroundWaitStarted();
                    ensureHeartbeat();
                }
                scheduleSettle(meta, grace);
            }
        }

        private void publishDashboardUsage(JsonNode payload) {
            if (payload == null || payload.isMissingNode() || payload.isNull()) {
                return;
            }
            JsonNode usageNode = payload.has("usage") ? payload.path("usage") : payload;
            OpenAiCompatibleStreamClient.TokenUsage current = HermesDashboardRpc.toUsage(usageNode);
            if (current == null) {
                return;
            }
            usage.set(current);
            if (usageListener != null) {
                usageListener.onUsage(current);
            }
        }

        private void handleSubagentEvent(String type, JsonNode payload) {
            // 仅在已有后台派工时，用子智能体事件延长 settle；
            // 同步委派的 subagent.* 发生在父轮内，不应误开 10 分钟等待。
            boolean backgroundWait = expectBackgroundFollowUp || !awaitingDelegateIds.isEmpty();
            if (backgroundWait) {
                expectBackgroundFollowUp = true;
                cancelSettle();
                markBackgroundWaitStarted();
                ensureHeartbeat();
            }

            String normalized = type == null ? "" : type.trim().toLowerCase(java.util.Locale.ROOT);
            if ("subagent.start".equals(normalized) || "subagent.spawn_requested".equals(normalized)) {
                liveSubagentCount.incrementAndGet();
            } else if ("subagent.complete".equals(normalized)) {
                liveSubagentCount.updateAndGet(n -> Math.max(0, n - 1));
            }

            OpenAiCompatibleStreamClient.ToolCallEvent parent = HermesDashboardRpc.toSubagentEvent(type, payload);
            if (parent != null && toolListener != null) {
                toolListener.onToolCall(parent);
            }
            OpenAiCompatibleStreamClient.ToolCallEvent nested = HermesDashboardRpc.toSubagentChildToolEvent(type, payload);
            if (nested != null && toolListener != null) {
                toolListener.onToolCall(nested);
            }

            String progress = summarizeSubagentProgress(type, payload);
            if (!progress.isBlank()) {
                lastSubagentProgress.set(progress);
                if (backgroundWait) {
                    pushAwaitingProgress(progress);
                }
            }
        }

        private static String summarizeSubagentProgress(String type, JsonNode payload) {
            if (payload == null || payload.isMissingNode()) {
                return "";
            }
            String goal = payload.path("goal").asText("").trim();
            String tool = payload.path("tool_name").asText("").trim();
            if (tool.isBlank()) {
                tool = payload.path("name").asText("").trim();
            }
            String text = payload.path("text").asText("").trim();
            if (text.isBlank()) {
                text = payload.path("tool_preview").asText("").trim();
            }
            if (text.isBlank()) {
                text = payload.path("summary").asText("").trim();
            }
            Integer idx = payload.path("task_index").isNumber() ? payload.path("task_index").asInt() : null;
            Integer total = payload.path("task_count").isNumber() ? payload.path("task_count").asInt() : null;
            String prefix = "";
            if (idx != null && total != null && total > 1) {
                prefix = "子任务 " + (idx + 1) + "/" + total + " · ";
            } else if (idx != null) {
                prefix = "子任务 " + (idx + 1) + " · ";
            }

            String normalized = type == null ? "" : type.trim().toLowerCase(java.util.Locale.ROOT);
            if (normalized.endsWith(".complete")) {
                String status = payload.path("status").asText("").trim().toLowerCase(java.util.Locale.ROOT);
                boolean failed = "error".equals(status) || "failed".equals(status)
                        || "timeout".equals(status) || "cancelled".equals(status);
                String label = failed ? "失败" : "完成";
                if (!goal.isBlank()) {
                    return prefix + "子智能体已" + label + "：" + truncateProgress(goal, 80);
                }
                if (!text.isBlank() && !HermesDashboardRpc.looksLikeJsonText(text)) {
                    return prefix + "子智能体已" + label + "：" + truncateProgress(text, 80);
                }
                return prefix + "子智能体已" + label;
            }
            if (normalized.endsWith(".tool")) {
                String label = tool.isBlank() ? "工具" : ClaudeCodeToolCatalog.fallbackDisplayName(tool);
                if (!text.isBlank() && !HermesDashboardRpc.looksLikeJsonText(text)) {
                    return prefix + "正在调用 " + label + "：" + truncateProgress(text, 80);
                }
                return prefix + "正在调用 " + label;
            }
            if (normalized.endsWith(".start") || normalized.endsWith(".spawn_requested")) {
                if (!goal.isBlank()) {
                    return prefix + "已启动：" + truncateProgress(goal, 100);
                }
                return prefix + "子智能体已启动";
            }
            if (!text.isBlank() && !HermesDashboardRpc.looksLikeJsonText(text)) {
                return prefix + truncateProgress(text, 120);
            }
            if (!goal.isBlank()) {
                return prefix + truncateProgress(goal, 120);
            }
            return "";
        }

        private static String truncateProgress(String s, int max) {
            if (s == null) {
                return "";
            }
            String t = s.replace('\n', ' ').trim();
            if (t.length() <= max) {
                return t;
            }
            return t.substring(0, max) + "…";
        }

        private void markBackgroundWaitStarted() {
            backgroundWaitStartedAt.compareAndSet(0L, System.currentTimeMillis());
        }

        private void ensureHeartbeat() {
            synchronized (settleLock) {
                if (heartbeatFuture != null && !heartbeatFuture.isDone()) {
                    return;
                }
                heartbeatFuture = TURN_SETTLE_SCHEDULER.scheduleAtFixedRate(() -> {
                    if (turnDone.isDone() || (!expectBackgroundFollowUp && awaitingDelegateIds.isEmpty())) {
                        cancelHeartbeat();
                        return;
                    }
                    int seq = backgroundHeartbeatSeq.incrementAndGet();
                    long started = backgroundWaitStartedAt.get();
                    long elapsedSec = started <= 0
                            ? 0
                            : Math.max(1, (System.currentTimeMillis() - started) / 1000L);
                    String last = lastSubagentProgress.get();
                    int live = liveSubagentCount.get();
                    StringBuilder sb = new StringBuilder();
                    sb.append("子智能体仍在后台执行（已等待 ").append(elapsedSec).append(" 秒");
                    if (live > 0) {
                        sb.append("，活跃 ").append(live).append(" 个");
                    }
                    sb.append("）");
                    if (last != null && !last.isBlank() && seq % 2 == 1) {
                        sb.append(" · ").append(truncateProgress(last, 100));
                    }
                    pushAwaitingProgress(sb.toString());
                }, BACKGROUND_DELEGATION_HEARTBEAT_MS, BACKGROUND_DELEGATION_HEARTBEAT_MS, TimeUnit.MILLISECONDS);
            }
        }

        private void cancelHeartbeat() {
            synchronized (settleLock) {
                ScheduledFuture<?> f = heartbeatFuture;
                heartbeatFuture = null;
                if (f != null) {
                    f.cancel(false);
                }
            }
        }

        private void pushAwaitingProgress(String text) {
            if (toolListener == null || text == null || text.isBlank() || awaitingDelegateIds.isEmpty()) {
                return;
            }
            for (String id : awaitingDelegateIds) {
                OpenAiCompatibleStreamClient.ToolCallEvent ev =
                        HermesDashboardRpc.awaitingProgress(id, text);
                if (ev != null) {
                    toolListener.onToolCall(ev);
                }
            }
        }

        private void finishAwaitingDelegates(String summary) {
            if (toolListener == null || awaitingDelegateIds.isEmpty()) {
                awaitingDelegateIds.clear();
                return;
            }
            for (String id : Set.copyOf(awaitingDelegateIds)) {
                OpenAiCompatibleStreamClient.ToolCallEvent ev =
                        HermesDashboardRpc.completeAwaiting(id, summary);
                if (ev != null) {
                    toolListener.onToolCall(ev);
                }
            }
            awaitingDelegateIds.clear();
            liveSubagentCount.set(0);
        }

        private void scheduleSettle(OpenAiCompatibleStreamClient.StreamCompletionMeta meta) {
            scheduleSettle(meta, settleGraceMs);
        }

        private void scheduleSettle(OpenAiCompatibleStreamClient.StreamCompletionMeta meta, long graceMs) {
            synchronized (settleLock) {
                cancelSettleLocked();
                pendingMeta = meta;
                long delay = Math.max(50L, graceMs);
                final boolean wasAwaiting = expectBackgroundFollowUp || !awaitingDelegateIds.isEmpty();
                settleFuture = TURN_SETTLE_SCHEDULER.schedule(() -> {
                    if (!turnDone.isDone()) {
                        cancelHeartbeat();
                        if (wasAwaiting && !awaitingDelegateIds.isEmpty()) {
                            finishAwaitingDelegates("等待子智能体汇总超时；可继续追问以获取最新结果");
                            if (consumer != null) {
                                consumer.onToken("\n\n（等待子智能体结果超时。任务可能仍在后台运行，可继续追问。）\n");
                            }
                        }
                        turnDone.complete(meta);
                    }
                }, delay, TimeUnit.MILLISECONDS);
            }
        }

        private void cancelSettle() {
            synchronized (settleLock) {
                cancelSettleLocked();
            }
        }

        private void cancelSettleLocked() {
            ScheduledFuture<?> f = settleFuture;
            settleFuture = null;
            if (f != null) {
                f.cancel(false);
            }
        }

        static boolean looksLikeGoalContinuation(String text) {
            if (text == null || text.isBlank()) {
                return false;
            }
            String t = text;
            return t.contains("Continuing toward goal")
                    || t.contains("↻")
                    || t.contains("继续推进")
                    || t.contains("继续朝目标");
        }

        static boolean looksLikeGoalTerminal(String text) {
            if (text == null || text.isBlank()) {
                return false;
            }
            String t = text;
            return t.contains("Goal achieved")
                    || t.contains("Goal paused")
                    || t.contains("Goal parked")
                    || t.contains("✓ Goal")
                    || t.contains("⏸ Goal")
                    || t.contains("⏳ Goal");
        }

        /**
         * 顶层 {@code delegate_task} 在 Hermes 上强制后台派工；tool.complete 的 result
         * 含 {@code status=dispatched}/{@code mode=background}（或等价文案）。
         */
        static boolean looksLikeBackgroundDelegation(String toolName, JsonNode payload) {
            String name = toolName == null ? "" : toolName.trim().toLowerCase(java.util.Locale.ROOT);
            if (!name.contains("delegate")) {
                return false;
            }
            if (payload == null || payload.isMissingNode() || payload.isNull()) {
                return true;
            }
            if (nodeLooksLikeBackgroundDelegation(payload.path("result"))) {
                return true;
            }
            for (String key : new String[] {"result_text", "resultText", "summary", "output"}) {
                String t = payload.path(key).asText("");
                if (textLooksLikeBackgroundDelegation(t)) {
                    return true;
                }
            }
            return false;
        }

        static boolean nodeLooksLikeBackgroundDelegation(JsonNode result) {
            if (result == null || result.isMissingNode() || result.isNull()) {
                return false;
            }
            if (result.isTextual()) {
                return textLooksLikeBackgroundDelegation(result.asText(""));
            }
            if (!result.isObject()) {
                return false;
            }
            String status = result.path("status").asText("").trim().toLowerCase(java.util.Locale.ROOT);
            String mode = result.path("mode").asText("").trim().toLowerCase(java.util.Locale.ROOT);
            if ("dispatched".equals(status) || "background".equals(mode) || "async".equals(mode)) {
                return true;
            }
            return textLooksLikeBackgroundDelegation(result.path("note").asText(""));
        }

        static boolean textLooksLikeBackgroundDelegation(String text) {
            if (text == null || text.isBlank()) {
                return false;
            }
            String t = text.toLowerCase(java.util.Locale.ROOT);
            return t.contains("\"mode\":\"background\"")
                    || t.contains("\"mode\": \"background\"")
                    || t.contains("\"status\":\"dispatched\"")
                    || t.contains("\"status\": \"dispatched\"")
                    || t.contains("running in the background")
                    || t.contains("running in parallel in the background")
                    || t.contains("在后台运行")
                    || t.contains("后台执行");
        }

        static boolean looksLikeBackgroundProcessNotice(String text) {
            if (text == null || text.isBlank()) {
                return false;
            }
            String t = text.toLowerCase(java.util.Locale.ROOT);
            return t.contains("async_delegation")
                    || t.contains("subagent")
                    || t.contains("子智能体")
                    || t.contains("delegation")
                    || t.contains("委派");
        }
    }
}
