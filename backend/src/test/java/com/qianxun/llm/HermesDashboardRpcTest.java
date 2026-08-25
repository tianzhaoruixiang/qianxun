package com.qianxun.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HermesDashboardRpcTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void request_shouldBeJsonRpc20() throws Exception {
        String json = HermesDashboardRpc.request(mapper, "q1", "prompt.submit", Map.of(
                "session_id", "abc",
                "text", "你好"
        ));
        var root = mapper.readTree(json);
        assertThat(root.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(root.path("id").asText()).isEqualTo("q1");
        assertThat(root.path("method").asText()).isEqualTo("prompt.submit");
        assertThat(root.path("params").path("text").asText()).isEqualTo("你好");
    }

    @Test
    void event_shouldMapDeltaToolAndUsage() throws Exception {
        var delta = mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.delta","session_id":"s1","payload":{"text":"你好"}}}
                """);
        assertThat(HermesDashboardRpc.eventType(delta)).isEqualTo("message.delta");
        assertThat(HermesDashboardRpc.eventSessionId(delta)).isEqualTo("s1");
        assertThat(HermesDashboardRpc.deltaText(HermesDashboardRpc.eventPayload(delta))).isEqualTo("你好");

        var start = mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"tool.start","session_id":"s1","payload":{"tool_id":"t1","name":"web_search","args_text":"{\\n  \\"query\\": \\"hi\\"\\n}","context":"hi"}}}
                """);
        var tc = HermesDashboardRpc.toToolEvent(
                HermesDashboardRpc.eventType(start), HermesDashboardRpc.eventPayload(start));
        assertThat(tc).isNotNull();
        assertThat(tc.toolCallId()).isEqualTo("t1");
        assertThat(tc.functionName()).isEqualTo("web_search");
        assertThat(tc.status()).isEqualTo("running");
        assertThat(tc.argsChunk()).contains("query");
        assertThat(tc.details()).containsEntry("context", "hi");
        assertThat(tc.details()).containsEntry("eventType", "tool.start");

        var done = mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.complete","session_id":"s1","payload":{"status":"complete","usage":{"input":120,"output":40,"total":160,"context_used":100,"context_max":128000,"context_percent":0.08}}}}
                """);
        var usage = HermesDashboardRpc.toUsage(HermesDashboardRpc.eventPayload(done).path("usage"));
        assertThat(usage).isNotNull();
        assertThat(usage.promptTokens()).isEqualTo(120);
        assertThat(usage.completionTokens()).isEqualTo(40);
        assertThat(usage.totalTokens()).isEqualTo(160);
        assertThat(usage.contextWindow()).isEqualTo(128000);
        assertThat(usage.contextUsed()).isEqualTo(100);
        assertThat(usage.contextPercent()).isEqualTo(0.08);
        assertThat(usage.sessionSnapshot()).isTrue();
    }

    @Test
    void toolComplete_shouldForwardFullArgsResultDurationAndSummary() throws Exception {
        var complete = mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"tool.complete","session_id":"s1","payload":{
                  "tool_id":"t1",
                  "name":"web_search",
                  "args":{"query":"低空经济"},
                  "result":{"data":{"web":[{"title":"a"},{"title":"b"}]}},
                  "duration_s":1.25,
                  "summary":"Did 2 searches in 1s",
                  "result_text":"{\\"data\\":{\\"web\\":[...]}}"
                }}}
                """);
        var tc = HermesDashboardRpc.toToolEvent(
                HermesDashboardRpc.eventType(complete), HermesDashboardRpc.eventPayload(complete));
        assertThat(tc).isNotNull();
        assertThat(tc.status()).isEqualTo("completed");
        assertThat(tc.argsChunk()).contains("低空经济");
        assertThat(tc.result()).contains("web");
        assertThat(tc.details()).containsEntry("summary", "Did 2 searches in 1s");
        assertThat(tc.details()).containsKey("durationSeconds");
        assertThat(((Number) tc.details().get("durationSeconds")).doubleValue()).isEqualTo(1.25);
        assertThat(tc.endedAt()).isNotNull();
        assertThat(tc.startedAt()).isNotNull();
        assertThat(tc.endedAt() - tc.startedAt()).isBetween(1200L, 1300L);
    }

    @Test
    void toolGeneratingAndOutputRisk_shouldMap() throws Exception {
        var gen = mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"tool.generating","session_id":"s1","payload":{"name":"write_file"}}}
                """);
        var g = HermesDashboardRpc.toToolEvent(
                HermesDashboardRpc.eventType(gen), HermesDashboardRpc.eventPayload(gen));
        assertThat(g).isNotNull();
        assertThat(g.functionName()).isEqualTo("write_file");
        assertThat(g.status()).isEqualTo("running");
        assertThat(g.details()).containsEntry("eventType", "tool.generating");

        var risk = mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"tool.output_risk","session_id":"s1","payload":{
                  "tool_id":"t9","name":"terminal","risk":"high","findings":["secret"],"redacted":true
                }}}
                """);
        var r = HermesDashboardRpc.toToolEvent(
                HermesDashboardRpc.eventType(risk), HermesDashboardRpc.eventPayload(risk));
        assertThat(r).isNotNull();
        assertThat(r.toolCallId()).isEqualTo("t9");
        assertThat(r.details()).containsEntry("risk", "high");
        assertThat(r.details()).containsEntry("redacted", true);
        assertThat(r.details().get("findings")).asList().containsExactly("secret");
    }

    @Test
    void toolError_shouldMapStatusError() throws Exception {
        var err = mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"tool.error","session_id":"s1","payload":{
                  "tool_id":"e1","name":"bash","error":"exit 1","result":"boom"
                }}}
                """);
        var tc = HermesDashboardRpc.toToolEvent(
                HermesDashboardRpc.eventType(err), HermesDashboardRpc.eventPayload(err));
        assertThat(tc).isNotNull();
        assertThat(tc.status()).isEqualTo("error");
        assertThat(tc.details()).containsEntry("error", "exit 1");
        assertThat(tc.result()).isEqualTo("boom");
    }

    @Test
    void nonToolEvent_shouldReturnNull() throws Exception {
        var delta = mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.delta","session_id":"s1","payload":{"text":"x"}}}
                """);
        assertThat(HermesDashboardRpc.toToolEvent(
                HermesDashboardRpc.eventType(delta), HermesDashboardRpc.eventPayload(delta))).isNull();
    }

    @Test
    void rpcError_shouldReadMessage() throws Exception {
        var err = mapper.readTree("""
                {"jsonrpc":"2.0","id":"q1","error":{"code":4001,"message":"no active session"}}
                """);
        assertThat(HermesDashboardRpc.isRpcResult(err)).isTrue();
        assertThat(HermesDashboardRpc.rpcId(err)).isEqualTo("q1");
        assertThat(HermesDashboardRpc.rpcError(err)).isEqualTo("no active session");
    }

    @Test
    void websocketOrigin_shouldSwitchScheme() {
        assertThat(HermesDashboardChatClient.websocketOrigin("http://hermes-agent:9119"))
                .isEqualTo("ws://hermes-agent:9119");
        assertThat(HermesDashboardChatClient.websocketOrigin("https://example.test/"))
                .isEqualTo("wss://example.test");
    }

    @Test
    void listener_shouldCompleteTurnOnMessageComplete() throws Exception {
        List<String> tokens = new ArrayList<>();
        List<OpenAiCompatibleStreamClient.ToolCallEvent> tools = new ArrayList<>();
        HermesDashboardChatClient.RpcSocket socket = new HermesDashboardChatClient.RpcSocket(
                mapper,
                tokens::add,
                tools::add,
                usage -> {},
                80L
        );
        socket.setSessionId("s1");
        var ready = mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}
                """);
        socket.handleNode(ready);
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.delta","session_id":"s1","payload":{"text":"甲"}}}
                """));
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"tool.start","session_id":"s1","payload":{"tool_id":"c1","name":"web_search","context":"q"}}}
                """));
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"tool.complete","session_id":"s1","payload":{"tool_id":"c1","name":"web_search","args":{"query":"q"},"result":{"ok":true},"duration_s":0.4}}}
                """));
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.complete","session_id":"s1","payload":{"status":"complete","usage":{"prompt":1,"completion":1,"total":2}}}}
                """));
        var meta = socket.awaitTurn(java.time.Duration.ofSeconds(2));
        assertThat(tokens).containsExactly("甲");
        assertThat(tools).hasSize(2);
        assertThat(tools.get(0).functionName()).isEqualTo("web_search");
        assertThat(tools.get(0).status()).isEqualTo("running");
        assertThat(tools.get(1).status()).isEqualTo("completed");
        assertThat(tools.get(1).argsChunk()).contains("query");
        assertThat(tools.get(1).result()).contains("ok");
        assertThat(meta.sawDone()).isTrue();
        assertThat(meta.usage()).isNotNull();
        assertThat(meta.usage().totalTokens()).isEqualTo(2);
    }

    @Test
    void listener_shouldNotFinishOnToolCompleteAlone() throws Exception {
        HermesDashboardChatClient.RpcSocket socket = new HermesDashboardChatClient.RpcSocket(
                mapper, t -> {}, tc -> {}, u -> {}, 80L);
        socket.setSessionId("s1");
        var toolDone = mapper.readTree(
                "{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"tool.complete\",\"session_id\":\"s1\",\"payload\":{\"tool_id\":\"c1\",\"name\":\"web_search\",\"result\":{\"ok\":true}}}}"
        );
        socket.handleNode(toolDone);
        try {
            socket.awaitTurn(java.time.Duration.ofMillis(200));
            throw new AssertionError("expected timeout when only tool.complete arrives");
        } catch (IllegalStateException ex) {
            assertThat(ex.getMessage()).contains("超时");
        }
    }

    @Test
    void listener_shouldWaitThroughGoalContinuationAfterMessageComplete() throws Exception {
        List<String> tokens = new ArrayList<>();
        HermesDashboardChatClient.RpcSocket socket = new HermesDashboardChatClient.RpcSocket(
                mapper, tokens::add, tc -> {}, u -> {}, 200L);
        socket.setSessionId("s1");
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.delta","session_id":"s1","payload":{"text":"第一轮"}}}
                """));
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.complete","session_id":"s1","payload":{"status":"complete","usage":{"prompt":1,"completion":1,"total":2}}}}
                """));
        // 模拟 Hermes /goal continuation：complete 后立刻 message.start，再跑第二轮
        Thread.sleep(40);
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.start","session_id":"s1","payload":{}}}
                """));
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"status.update","session_id":"s1","payload":{"kind":"goal","text":"继续推进目标"}}}
                """));
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.delta","session_id":"s1","payload":{"text":"+第二轮"}}}
                """));
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.complete","session_id":"s1","payload":{"status":"complete","usage":{"prompt":3,"completion":2,"total":5}}}}
                """));
        var meta = socket.awaitTurn(java.time.Duration.ofSeconds(3));
        assertThat(tokens).anySatisfy(t -> assertThat(t).contains("第一轮"));
        assertThat(tokens).anySatisfy(t -> assertThat(t).contains("第二轮"));
        assertThat(tokens).anySatisfy(t -> assertThat(t).contains("继续推进目标"));
        assertThat(meta.sawDone()).isTrue();
        assertThat(meta.usage().totalTokens()).isEqualTo(5);
    }

    @Test
    void listener_shouldCancelSettleWhenSessionInfoSaysRunning() throws Exception {
        List<String> tokens = new ArrayList<>();
        HermesDashboardChatClient.RpcSocket socket = new HermesDashboardChatClient.RpcSocket(
                mapper, tokens::add, tc -> {}, u -> {}, 200L);
        socket.setSessionId("s1");
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.complete","session_id":"s1","payload":{"status":"complete","usage":{"prompt":1,"completion":1,"total":1}}}}
                """));
        Thread.sleep(30);
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"session.info","session_id":"s1","payload":{"running":true}}}
                """));
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.delta","session_id":"s1","payload":{"text":"续跑"}}}
                """));
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.complete","session_id":"s1","payload":{"status":"complete","usage":{"prompt":2,"completion":2,"total":4}}}}
                """));
        var meta = socket.awaitTurn(java.time.Duration.ofSeconds(3));
        assertThat(tokens).contains("续跑");
        assertThat(meta.usage().totalTokens()).isEqualTo(4);
    }

    @Test
    void listener_shouldWaitForBackgroundDelegationFollowUp() throws Exception {
        List<String> tokens = new ArrayList<>();
        List<OpenAiCompatibleStreamClient.ToolCallEvent> tools = new ArrayList<>();
        HermesDashboardChatClient.RpcSocket socket = new HermesDashboardChatClient.RpcSocket(
                mapper, tokens::add, tools::add, u -> {}, 80L);
        socket.setSessionId("s1");
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.delta","session_id":"s1","payload":{"text":"已派工"}}}
                """));
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"tool.complete","session_id":"s1","payload":{"tool_id":"d1","name":"delegate_task","result":{"status":"dispatched","mode":"background","count":3},"duration_s":0.2}}}
                """));
        assertThat(tools).isNotEmpty();
        assertThat(tools.get(tools.size() - 1).status()).isEqualTo("awaiting");
        assertThat(tools.get(tools.size() - 1).details().get("awaitingBackground")).isEqualTo(true);

        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"subagent.start","session_id":"s1","payload":{"subagent_id":"c1","goal":"检索国家级政策","task_index":0,"task_count":3}}}
                """));
        assertThat(tools.stream().anyMatch(t ->
                "subagent".equals(t.functionName()) && "running".equals(t.status()))).isTrue();
        assertThat(tools.stream().anyMatch(t ->
                "awaiting".equals(t.status()) && t.details() != null
                        && String.valueOf(t.details().get("summary")).contains("已启动"))).isTrue();

        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"subagent.tool","session_id":"s1","payload":{"subagent_id":"c1","goal":"检索国家级政策","tool_name":"web_search","text":"低空经济","task_index":0,"task_count":3}}}
                """));
        assertThat(tools.stream().anyMatch(t ->
                "awaiting".equals(t.status()) && t.details() != null
                        && String.valueOf(t.details().get("summary")).contains("网页搜索"))).isTrue();

        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.complete","session_id":"s1","payload":{"status":"complete","usage":{"prompt":1,"completion":1,"total":2}}}}
                """));
        // 若未识别后台派工，80ms settle 后会提前结束；此处模拟数十毫秒后的汇入续轮
        Thread.sleep(120);
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"status.update","session_id":"s1","payload":{"kind":"process","text":"子智能体任务已完成"}}}
                """));
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.start","session_id":"s1","payload":{}}}
                """));
        assertThat(tools.stream().anyMatch(t ->
                "d1".equals(t.toolCallId()) && "completed".equals(t.status()))).isTrue();
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.delta","session_id":"s1","payload":{"text":"汇总报告如下"}}}
                """));
        socket.handleNode(mapper.readTree("""
                {"jsonrpc":"2.0","method":"event","params":{"type":"message.complete","session_id":"s1","payload":{"status":"complete","usage":{"prompt":5,"completion":4,"total":9}}}}
                """));
        var meta = socket.awaitTurn(java.time.Duration.ofSeconds(3));
        assertThat(tokens).anySatisfy(t -> assertThat(t).contains("已派工"));
        assertThat(tokens).anySatisfy(t -> assertThat(t).contains("汇总报告如下"));
        assertThat(meta.sawDone()).isTrue();
        assertThat(meta.usage().totalTokens()).isEqualTo(9);
    }

    @Test
    void toToolEvent_shouldMarkStructuredFailuresAsError() throws Exception {
        record Case(String result, String expectedErrorPart) {}
        var failing = List.of(
                new Case("{\\\"success\\\": false, \\\"error\\\": \\\"域名解析失败\\\"}", "域名解析失败"),
                new Case("{\\\"ok\\\": false, \\\"message\\\": \\\"文件不存在\\\"}", "文件不存在"),
                new Case("{\\\"status\\\": \\\"timeout\\\"}", "工具执行失败"),
                new Case("{\\\"exit_code\\\": 2, \\\"stderr\\\": \\\"命令未找到\\\"}", "命令未找到"),
                new Case("{\\\"exit_code\\\": 1}", "退出码 1"),
                new Case("{\\\"blocked_by_policy\\\": true}", "工具执行失败")
        );
        for (Case c : failing) {
            var node = mapper.readTree("""
                    {"jsonrpc":"2.0","method":"event","params":{"type":"tool.complete","session_id":"s1","payload":{"tool_id":"t1","name":"terminal","result":"%s"}}}
                    """.formatted(c.result()));
            var tc = HermesDashboardRpc.toToolEvent(
                    HermesDashboardRpc.eventType(node), HermesDashboardRpc.eventPayload(node));
            assertThat(tc).isNotNull();
            assertThat(tc.status()).as("result=%s", c.result()).isEqualTo("error");
            assertThat(String.valueOf(tc.details().get("error"))).contains(c.expectedErrorPart());
        }
    }

    @Test
    void toToolEvent_shouldKeepSuccessfulResultsCompleted() throws Exception {
        var succeeding = List.of(
                "{\\\"success\\\": true, \\\"data\\\": {\\\"web\\\": []}}",
                "{\\\"ok\\\": true, \\\"exit_code\\\": 0, \\\"stdout\\\": \\\"done\\\"}",
                "{\\\"status\\\": \\\"running\\\", \\\"pid\\\": 42}",
                "{\\\"message\\\": \\\"已写入 12 行\\\"}"
        );
        for (String result : succeeding) {
            var node = mapper.readTree("""
                    {"jsonrpc":"2.0","method":"event","params":{"type":"tool.complete","session_id":"s1","payload":{"tool_id":"t1","name":"terminal","result":"%s"}}}
                    """.formatted(result));
            var tc = HermesDashboardRpc.toToolEvent(
                    HermesDashboardRpc.eventType(node), HermesDashboardRpc.eventPayload(node));
            assertThat(tc).isNotNull();
            assertThat(tc.status()).as("result=%s", result).isEqualTo("completed");
            assertThat(tc.details()).doesNotContainKey("error");
        }
    }

    @Test
    void toSubagentEvent_shouldMarkFailedSummaryAsError() {
        var payload = mapper.createObjectNode();
        payload.put("subagent_id", "sa-9");
        payload.put("goal", "抓取政策");
        payload.put("summary", "{\"success\": false, \"error\": \"抓取超时\"}");
        var ev = HermesDashboardRpc.toSubagentEvent("subagent.complete", payload);
        assertThat(ev).isNotNull();
        assertThat(ev.status()).isEqualTo("error");
        assertThat(String.valueOf(ev.details().get("error"))).contains("抓取超时");
    }

    @Test
    void toSubagentEvent_shouldMapChildProgress() {
        var payload = mapper.createObjectNode();
        payload.put("subagent_id", "sa-1");
        payload.put("goal", "写报告");
        payload.put("task_index", 1);
        payload.put("task_count", 2);
        payload.put("tool_name", "write_file");
        payload.put("text", "report.md");
        var ev = HermesDashboardRpc.toSubagentEvent("subagent.tool", payload);
        assertThat(ev).isNotNull();
        assertThat(ev.toolCallId()).isEqualTo("sa-1");
        assertThat(ev.functionName()).isEqualTo("subagent");
        assertThat(ev.status()).isEqualTo("running");
        assertThat(ev.details().get("childToolName")).isEqualTo("write_file");
        assertThat(HermesDashboardRpc.toSubagentEvent("subagent.text", payload)).isNull();
    }

    @Test
    void backgroundDelegationHelpers_shouldDetectDispatchedResult() {
        var payload = mapper.createObjectNode();
        var result = mapper.createObjectNode();
        result.put("status", "dispatched");
        result.put("mode", "background");
        payload.set("result", result);
        assertThat(HermesDashboardChatClient.RpcSocket.looksLikeBackgroundDelegation(
                "delegate_task", payload)).isTrue();

        var other = mapper.createObjectNode();
        other.set("result", mapper.createObjectNode().put("status", "dispatched"));
        assertThat(HermesDashboardChatClient.RpcSocket.looksLikeBackgroundDelegation(
                "web_search", other)).isFalse();

        assertThat(HermesDashboardChatClient.RpcSocket.textLooksLikeBackgroundDelegation(
                "3 subagents are running in parallel in the background.")).isTrue();
        assertThat(HermesDashboardChatClient.RpcSocket.looksLikeBackgroundProcessNotice(
                "async_delegation batch finished")).isTrue();
    }

    @Test
    void goalStatusHelpers_shouldDetectContinueAndTerminal() {
        assertThat(HermesDashboardChatClient.RpcSocket.looksLikeGoalContinuation(
                "↻ Continuing toward goal (2/20): still working")).isTrue();
        assertThat(HermesDashboardChatClient.RpcSocket.looksLikeGoalTerminal(
                "✓ Goal achieved: files ready")).isTrue();
        assertThat(HermesDashboardChatClient.RpcSocket.looksLikeGoalTerminal(
                "⏸ Goal paused — 20/20 turns used")).isTrue();
        assertThat(HermesDashboardChatClient.RpcSocket.looksLikeGoalContinuation(
                "✓ Goal achieved: done")).isFalse();
    }

    @Test
    void needsCommandDispatchFallback_shouldDetectSkillErrors() {
        assertThat(HermesDashboardChatClient.needsCommandDispatchFallback(
                "skill command: use command.dispatch for /llm-wiki")).isTrue();
        assertThat(HermesDashboardChatClient.needsCommandDispatchFallback(
                "斜杠指令失败: skill command: use command.dispatch for /llm-wiki")).isTrue();
        assertThat(HermesDashboardChatClient.needsCommandDispatchFallback(
                "use command.dispatch for /foo")).isTrue();
        assertThat(HermesDashboardChatClient.needsCommandDispatchFallback(
                "Unknown command: /smart-charts-600")).isTrue();
        assertThat(HermesDashboardChatClient.needsCommandDispatchFallback(
                "斜杠指令失败: Unknown command: /smart-charts-600")).isTrue();
        assertThat(HermesDashboardChatClient.needsCommandDispatchFallback(
                "not a quick/plugin/bundle/skill command: foo")).isFalse();
        assertThat(HermesDashboardChatClient.needsCommandDispatchFallback(
                "empty command")).isFalse();
        assertThat(HermesDashboardChatClient.needsCommandDispatchFallback(
                "session not found")).isFalse();
    }

    @Test
    void unknownSkillSlash_shouldFallbackToPromptInsteadOfBuiltins() {
        assertThat(HermesDashboardChatClient.looksLikeUnknownSlash(
                "Unknown command: /smart-charts-600")).isTrue();
        assertThat(HermesDashboardChatClient.looksLikeUnknownSlash(
                "not a quick/plugin/skill command: smart-charts-600")).isTrue();
        assertThat(HermesDashboardChatClient.looksLikeSkillSlash("/smart-charts-600 画图")).isTrue();
        assertThat(HermesDashboardChatClient.looksLikeSkillSlash("/goal 调研")).isFalse();
        assertThat(HermesDashboardChatClient.looksLikeSkillSlash("/agents")).isFalse();
        assertThat(HermesDashboardChatClient.canFallbackSkillPrompt(
                "Unknown command: /smart-charts-600", "/smart-charts-600 画图")).isTrue();
        assertThat(HermesDashboardChatClient.canFallbackSkillPrompt(
                "Unknown command: /goal", "/goal 调研")).isFalse();
    }

    @Test
    void isSessionMissing_shouldDetectStaleUpstreamSession() {
        assertThat(HermesDashboardChatClient.isSessionMissing(
                new IllegalStateException("上游对话失败: session not found"))).isTrue();
        assertThat(HermesDashboardChatClient.isSessionMissingMessage("Unknown session")).isTrue();
        assertThat(HermesDashboardChatClient.isSessionMissing(
                new IllegalStateException("timeout"))).isFalse();
    }

    @Test
    void parseSlashCommand_shouldSplitNameAndArgForDispatch() throws Exception {
        var parts = HermesDashboardChatClient.parseSlashCommand("/llm-wiki 写一篇摘要");
        assertThat(parts.name()).isEqualTo("llm-wiki");
        assertThat(parts.arg()).isEqualTo("写一篇摘要");

        var bare = HermesDashboardChatClient.parseSlashCommand("case-brief");
        assertThat(bare.name()).isEqualTo("case-brief");
        assertThat(bare.arg()).isEmpty();

        String json = HermesDashboardRpc.request(mapper, "q2", "command.dispatch", Map.of(
                "session_id", "s1",
                "name", parts.name(),
                "arg", parts.arg()
        ));
        var root = mapper.readTree(json);
        assertThat(root.path("method").asText()).isEqualTo("command.dispatch");
        assertThat(root.path("params").path("name").asText()).isEqualTo("llm-wiki");
        assertThat(root.path("params").path("arg").asText()).isEqualTo("写一篇摘要");
    }
}
