package com.qianxun.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.config.QianxunProperties;
import com.qianxun.service.ChatAgentsInvocation;
import com.qianxun.service.ChatDashboardTurn;
import com.qianxun.service.ChatSlashCommandSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 通过 HTTP NDJSON 调用独立容器中的 Claude Agent SDK 网关（{@code POST /v1/agent/stream}）。
 */
@Component
public class ClaudeCodeChatClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeChatClient.class);

    private final HermesAgentClient hermes;
    private final ObjectMapper objectMapper;
    private final QianxunProperties properties;
    private final HttpClient http;

    public ClaudeCodeChatClient(
            HermesAgentClient hermes,
            ObjectMapper objectMapper,
            QianxunProperties properties
    ) {
        this.hermes = hermes;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.http = hermes.httpClient();
    }

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
        if (!properties.getClaude().isEnabled()) {
            throw new IllegalStateException("未启用 Claude Code 运行器");
        }
        String origin = hermes.origin();
        if (origin.isBlank()) {
            throw new IllegalStateException("未配置 Claude Code 网关地址（qianxun.claude.base-url）");
        }
        throwIfCancelled(cancelled);

        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", cacheKey == null ? "" : cacheKey);
        body.put("userId", workspaceOwnerId == null ? "" : workspaceOwnerId);
        body.put("profile", ClaudeCodePaths.normalizeProfileName(profile));
        body.put("prompt", buildPrompt(plan, "resume-unknown"));
        if (plan != null && plan.seedHistory() != null) {
            body.put("seedHistory", plan.seedHistory());
        }
        body.put("model", blankOr(properties.getClaude().getChatModel(), "claude-sonnet-4-5"));
        body.put("permissionMode", blankOr(properties.getClaude().getPermissionMode(), "bypassPermissions"));
        List<String> enabled = hermes.listChatGatewayEnabledToolsets(workspaceOwnerId, profile);
        if (!enabled.isEmpty()) {
            body.put("allowedToolsets", enabled);
        }
        if (!hermes.isChatMcpAllowed(workspaceOwnerId, profile)) {
            body.put("mcpDisabled", true);
        }

        int timeoutSec = properties.getLlm().getStreamTimeoutSeconds();
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(origin + "/v1/agent/stream"))
                .timeout(timeoutSec > 0 ? Duration.ofSeconds(timeoutSec) : Duration.ofDays(7))
                .header("Content-Type", "application/json")
                .header("Accept", "application/x-ndjson")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        String gatewayKey = properties.getClaude().getApiKey();
        if (gatewayKey != null && !gatewayKey.isBlank()) {
            req.header("Authorization", "Bearer " + gatewayKey.trim());
        }
        String traceId = com.qianxun.context.TraceContext.get();
        if (traceId != null && !traceId.isBlank()) {
            req.header(com.qianxun.context.TraceContext.HEADER, traceId);
        }

        log.info("Claude Code HTTP 流 session={} profile={}", truncate(cacheKey, 36),
                ClaudeCodePaths.normalizeProfileName(profile));

        HttpResponse<InputStream> res = http.send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
        InputStream in = res.body();
        AtomicReference<InputStream> live = new AtomicReference<>(in);
        if (registerCancel != null) {
            registerCancel.accept(() -> {
                InputStream s = live.get();
                if (s != null) {
                    try {
                        s.close();
                    } catch (Exception ignored) {
                        /* closed */
                    }
                }
            });
        }
        try {
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                String err = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("Claude Code 网关 HTTP " + res.statusCode() + ": " + truncate(err, 800));
            }

            ClaudeCodeStreamParser parser = new ClaudeCodeStreamParser(objectMapper);
            OpenAiCompatibleStreamClient.TokenUsage lastUsage = null;
            boolean sawDone = false;
            String finishReason = "stop";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    throwIfCancelled(cancelled);
                    ClaudeCodeStreamParser.ParseResult r = parser.accept(line);
                    if (r.token() != null && !r.token().isEmpty() && consumer != null) {
                        consumer.onToken(r.token());
                    }
                    if (toolListener != null && r.tools() != null) {
                        for (OpenAiCompatibleStreamClient.ToolCallEvent ev : r.tools()) {
                            toolListener.onToolCall(ev);
                        }
                    }
                    if (r.usage() != null) {
                        lastUsage = r.usage();
                        if (usageListener != null) {
                            usageListener.onUsage(r.usage());
                        }
                    }
                    if (r.resultDone()) {
                        sawDone = true;
                        finishReason = r.finishReason() == null || r.finishReason().isBlank()
                                ? "stop" : r.finishReason();
                        if (r.error() != null && !r.error().isBlank()) {
                            throw new IllegalStateException(r.error());
                        }
                    }
                    if (r.error() != null && !r.error().isBlank() && !r.resultDone()) {
                        throw new IllegalStateException(r.error());
                    }
                }
            }
            return new OpenAiCompatibleStreamClient.StreamCompletionMeta(sawDone, finishReason, lastUsage);
        } finally {
            live.set(null);
            try {
                in.close();
            } catch (Exception ignored) {
                /* closed */
            }
        }
    }

    static String buildPrompt(ChatDashboardTurn.Plan plan, String resumeId) {
        if (plan == null) {
            return "";
        }
        String slash = plan.slashCommand() == null ? "" : plan.slashCommand().trim();
        String user = plan.promptText() == null ? "" : plan.promptText().trim();
        if (!slash.isBlank()) {
            return rewriteSlash(slash, user, plan.expectSendThenPrompt());
        }
        return user;
    }

    static String rewriteSlash(String slash, String user, boolean expectThenPrompt) {
        String s = slash.trim();
        String lower = s.toLowerCase();
        if (lower.equals("/goal clear") || lower.startsWith("/goal clear")) {
            return "请停止当前长程目标，之后按普通对话回答。"
                    + (user.isBlank() ? "" : "\n\n" + user);
        }
        if (lower.equals(ChatAgentsInvocation.HERMES_COMMAND)
                || lower.startsWith("/agents")) {
            return "请列出当前会话中的子智能体与运行中任务。若没有，请明确说明当前没有运行中的委派任务。";
        }
        if (lower.startsWith("/goal")) {
            String goal = s.substring("/goal".length()).trim();
            StringBuilder sb = new StringBuilder();
            sb.append("请将以下内容作为本会话的长程目标，并开始执行。在达成成功标准前不要停止。\n");
            sb.append(goal);
            if (expectThenPrompt && !user.isBlank()) {
                sb.append("\n\n用户补充：").append(user);
            }
            return sb.toString();
        }
        if (s.startsWith("/")) {
            return s;
        }
        // 透传 Claude Code 原生斜杠（/compact、/mcp、/model 等）
        if (ChatSlashCommandSupport.isPassthroughOnly(s)) {
            return user.isBlank() ? s : s + "\n\n" + user;
        }
        return user.isBlank() ? s : s + "\n\n" + user;
    }

    private static void throwIfCancelled(BooleanSupplier cancelled) {
        if (cancelled != null && cancelled.getAsBoolean()) {
            throw new CancellationException("run cancelled");
        }
    }

    private static String blankOr(String v, String d) {
        return v == null || v.isBlank() ? d : v.trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
