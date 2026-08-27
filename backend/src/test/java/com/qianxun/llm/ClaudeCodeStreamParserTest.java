package com.qianxun.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeCodeStreamParserTest {

    private final ClaudeCodeStreamParser parser = new ClaudeCodeStreamParser(new ObjectMapper());

    @Test
    void streamEvent_shouldEmitTextDelta() throws Exception {
        ClaudeCodeStreamParser.ParseResult r = parser.accept("""
                {"type":"stream_event","session_id":"sess_1","event":{"delta":{"type":"text_delta","text":"你好"}}}
                """);
        assertThat(r.token()).isEqualTo("你好");
        assertThat(r.sessionId()).isEqualTo("sess_1");
        assertThat(r.resultDone()).isFalse();
    }

    @Test
    void assistant_shouldSkipFullTextAfterPartialDeltas() throws Exception {
        parser.accept("""
                {"type":"stream_event","event":{"delta":{"type":"text_delta","text":"你"}}}
                """);
        ClaudeCodeStreamParser.ParseResult r = parser.accept("""
                {"type":"assistant","session_id":"s","message":{"content":[{"type":"text","text":"你好"}]}}
                """);
        assertThat(r.token()).isEmpty();
    }

    @Test
    void assistant_shouldEmitToolUse() throws Exception {
        ClaudeCodeStreamParser.ParseResult r = parser.accept("""
                {"type":"assistant","session_id":"s","message":{"content":[
                  {"type":"tool_use","id":"t1","name":"Bash","input":{"command":"ls"}}
                ]}}
                """);
        assertThat(r.tools()).hasSize(1);
        assertThat(r.tools().get(0).toolCallId()).isEqualTo("t1");
        assertThat(r.tools().get(0).functionName()).isEqualTo("Bash");
        assertThat(r.tools().get(0).status()).isEqualTo("running");
        assertThat(r.tools().get(0).argsChunk()).contains("ls");
    }

    @Test
    void streamEvent_shouldEmitToolUseOnContentBlockStart() throws Exception {
        ClaudeCodeStreamParser.ParseResult r = parser.accept("""
                {"type":"stream_event","session_id":"s","event":{"type":"content_block_start","content_block":{
                  "type":"tool_use","id":"toolu_1","name":"WebSearch","input":{"query":"qianyu"}
                }}}
                """);
        assertThat(r.tools()).hasSize(1);
        assertThat(r.tools().get(0).toolCallId()).isEqualTo("toolu_1");
        assertThat(r.tools().get(0).functionName()).isEqualTo("WebSearch");
        assertThat(r.tools().get(0).status()).isEqualTo("running");
    }

    @Test
    void user_shouldEmitToolResult() throws Exception {
        ClaudeCodeStreamParser.ParseResult r = parser.accept("""
                {"type":"user","message":{"content":[
                  {"type":"tool_result","tool_use_id":"t1","content":"ok"}
                ]}}
                """);
        assertThat(r.tools()).hasSize(1);
        assertThat(r.tools().get(0).toolCallId()).isEqualTo("t1");
        assertThat(r.tools().get(0).status()).isEqualTo("completed");
        assertThat(r.tools().get(0).result()).isEqualTo("ok");
    }

    @Test
    void result_shouldMarkDoneAndParseUsage() throws Exception {
        ClaudeCodeStreamParser.ParseResult r = parser.accept("""
                {"type":"result","subtype":"success","session_id":"s",
                 "usage":{"input_tokens":10,"output_tokens":5}}
                """);
        assertThat(r.resultDone()).isTrue();
        assertThat(r.finishReason()).isEqualTo("stop");
        assertThat(r.usage().promptTokens()).isEqualTo(10);
        assertThat(r.usage().completionTokens()).isEqualTo(5);
        assertThat(r.usage().totalTokens()).isEqualTo(15);
        assertThat(r.usage().liveOccupancy()).isFalse();
    }

    @Test
    void assistant_shouldEmitLiveUsage() throws Exception {
        ClaudeCodeStreamParser.ParseResult r = parser.accept("""
                {"type":"assistant","session_id":"s","message":{
                  "usage":{"input_tokens":1000,"output_tokens":40},
                  "content":[{"type":"text","text":"hi"}]
                }}
                """);
        assertThat(r.usage()).isNotNull();
        assertThat(r.usage().liveOccupancy()).isTrue();
        assertThat(r.usage().promptTokens()).isEqualTo(1000);
        assertThat(r.usage().completionTokens()).isEqualTo(40);
        assertThat(r.usage().contextUsed()).isEqualTo(1040);
    }

    @Test
    void errorLine_shouldSurfaceMessage() throws Exception {
        ClaudeCodeStreamParser.ParseResult r = parser.accept("""
                {"type":"error","error":"未配置 ANTHROPIC_API_KEY"}
                """);
        assertThat(r.error()).isEqualTo("未配置 ANTHROPIC_API_KEY");
        assertThat(r.resultDone()).isFalse();
    }

    @Test
    void heartbeat_shouldBeIgnored() throws Exception {
        ClaudeCodeStreamParser.ParseResult r = parser.accept("""
                {"type":"heartbeat","ts":1710000000000}
                """);
        assertThat(r.token()).isEmpty();
        assertThat(r.resultDone()).isFalse();
        assertThat(r.error()).isEmpty();
        assertThat(r.tools()).isEmpty();
    }

    @Test
    void nestedAssistant_shouldNotLeakTokensAndShouldTagChildTools() throws Exception {
        ClaudeCodeStreamParser.ParseResult r = parser.accept("""
                {"type":"assistant","session_id":"s","parent_tool_use_id":"agent_1","message":{"content":[
                  {"type":"text","text":"开始检索"},
                  {"type":"tool_use","id":"c1","name":"WebSearch","input":{"query":"低空经济"}}
                ]}}
                """);
        assertThat(r.token()).isEmpty();
        assertThat(r.resultDone()).isFalse();
        assertThat(r.tools()).hasSize(2);
        assertThat(r.tools().get(0).toolCallId()).isEqualTo("agent_1");
        assertThat(r.tools().get(0).details().get("summary").toString()).contains("开始检索");
        assertThat(r.tools().get(1).toolCallId()).isEqualTo("c1");
        assertThat(r.tools().get(1).functionName()).isEqualTo("WebSearch");
        assertThat(r.tools().get(1).details().get("parentId")).isEqualTo("agent_1");
        assertThat(r.tools().get(1).details().get("subagent")).isEqualTo(true);
    }

    @Test
    void nestedStreamTextDelta_shouldNotEnterParentBubble() throws Exception {
        ClaudeCodeStreamParser.ParseResult r = parser.accept("""
                {"type":"stream_event","session_id":"s","parent_tool_use_id":"agent_1",
                 "event":{"delta":{"type":"text_delta","text":"子任务口播"}}}
                """);
        assertThat(r.token()).isEmpty();
        assertThat(r.tools()).isEmpty();
        assertThat(r.resultDone()).isFalse();
    }

    @Test
    void nestedResult_shouldNotFinishParentTurn() throws Exception {
        ClaudeCodeStreamParser.ParseResult r = parser.accept("""
                {"type":"result","subtype":"success","session_id":"s","parent_tool_use_id":"agent_1",
                 "result":"子任务完成","is_error":false}
                """);
        assertThat(r.resultDone()).isFalse();
        assertThat(r.error()).isEmpty();
        assertThat(r.tools()).hasSize(1);
        assertThat(r.tools().get(0).toolCallId()).isEqualTo("agent_1");
        assertThat(r.tools().get(0).status()).isEqualTo("completed");
        assertThat(r.tools().get(0).result()).contains("子任务完成");
    }

    @Test
    void nestedResult_error_shouldMarkParentFailed() throws Exception {
        ClaudeCodeStreamParser.ParseResult r = parser.accept("""
                {"type":"result","subtype":"error","session_id":"s","parent_tool_use_id":"agent_1",
                 "error":"子任务失败","is_error":true}
                """);
        assertThat(r.resultDone()).isFalse();
        assertThat(r.tools()).hasSize(1);
        assertThat(r.tools().get(0).status()).isEqualTo("error");
    }

    @Test
    void nestedUser_shouldCompleteChildTool() throws Exception {
        ClaudeCodeStreamParser.ParseResult r = parser.accept("""
                {"type":"user","parent_tool_use_id":"agent_1","message":{"content":[
                  {"type":"tool_result","tool_use_id":"c1","content":"hits=3"}
                ]}}
                """);
        assertThat(r.tools()).hasSize(1);
        assertThat(r.tools().get(0).toolCallId()).isEqualTo("c1");
        assertThat(r.tools().get(0).status()).isEqualTo("completed");
        assertThat(r.tools().get(0).details().get("parentId")).isEqualTo("agent_1");
    }

    @Test
    void blankOrNonJson_shouldBeIgnored() throws Exception {
        assertThat(parser.accept("").token()).isEmpty();
        assertThat(parser.accept("not-json").token()).isEmpty();
    }

    @Test
    void compactLine_shouldExposePhase() throws Exception {
        ClaudeCodeStreamParser.ParseResult start = parser.accept("""
                {"type":"compact","phase":"start","session_id":"s"}
                """);
        assertThat(start.compact().phase()).isEqualTo("start");
        assertThat(start.resultDone()).isFalse();

        ClaudeCodeStreamParser.ParseResult done = parser.accept("""
                {"type":"system","subtype":"compact_boundary","session_id":"s",
                 "compact_metadata":{"trigger":"auto","pre_tokens":180000}}
                """);
        assertThat(done.compact().phase()).isEqualTo("done");
        assertThat(done.compact().trigger()).isEqualTo("auto");
        assertThat(done.compact().preTokens()).isEqualTo(180000);
    }

    @Test
    void compactingStatus_shouldMapToStart() throws Exception {
        ClaudeCodeStreamParser.ParseResult r = parser.accept("""
                {"type":"system","subtype":"status","status":"compacting","session_id":"s"}
                """);
        assertThat(r.compact().phase()).isEqualTo("start");
    }
}
