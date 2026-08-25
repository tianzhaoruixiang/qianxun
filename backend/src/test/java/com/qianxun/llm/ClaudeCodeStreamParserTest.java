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
    void blankOrNonJson_shouldBeIgnored() throws Exception {
        assertThat(parser.accept("").token()).isEmpty();
        assertThat(parser.accept("not-json").token()).isEmpty();
    }
}
