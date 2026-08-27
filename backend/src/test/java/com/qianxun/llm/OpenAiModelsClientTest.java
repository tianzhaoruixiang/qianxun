package com.qianxun.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiModelsClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void modelsUrl_appendsModels() {
        assertThat(OpenAiModelsClient.modelsUrl("https://dashscope.aliyuncs.com/compatible-mode/v1/"))
                .isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1/models");
        assertThat(OpenAiModelsClient.modelsUrl("http://host.docker.internal:8000/v1/models"))
                .isEqualTo("http://host.docker.internal:8000/v1/models");
    }

    @Test
    void parseModelIds_openaiShape() throws Exception {
        var root = mapper.readTree("""
                {"object":"list","data":[{"id":"qwen3-plus"},{"id":"qwen3.6-plus"},{"id":"qwen-max"}]}
                """);
        assertThat(OpenAiModelsClient.parseModelIds(root))
                .containsExactly("qwen3-plus", "qwen3.6-plus", "qwen-max");
    }

    @Test
    void parseModelIds_nameFallbackAndDedup() throws Exception {
        var root = mapper.readTree("""
                {"models":[{"name":"qwen3-plus"},{"id":"qwen3-plus"},"DeepseekV4Flash"]}
                """);
        assertThat(OpenAiModelsClient.parseModelIds(root))
                .containsExactly("qwen3-plus", "DeepseekV4Flash");
    }

    @Test
    void parseModelInfos_keepsContextWindow() throws Exception {
        var root = mapper.readTree("""
                {"data":[
                  {"id":"qwen3-plus","context_length":131072},
                  {"id":"qwen-max"}
                ]}
                """);
        var items = OpenAiModelsClient.parseModelInfos(root);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).id()).isEqualTo("qwen3-plus");
        assertThat(items.get(0).contextWindow()).isEqualTo(131072);
        assertThat(items.get(1).contextWindow()).isZero();
    }

    @Test
    void findContextWindowInList_readsModelInfo() throws Exception {
        var root = mapper.readTree("""
                {"data":[
                  {"id":"openai/qwen3.6-plus","model_info":{"max_input_tokens":131072}},
                  {"id":"other","context_window":8000}
                ]}
                """);
        assertThat(OpenAiModelsClient.findContextWindowInList(root, "qwen3.6-plus")).isEqualTo(131072);
        assertThat(OpenAiModelsClient.findContextWindowInList(root, "missing")).isZero();
    }

    @Test
    void listModelIds_readsOpenAiList() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            byte[] body = "{\"data\":[{\"id\":\"qwen3-plus\"},{\"id\":\"qwen-max\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            OpenAiModelsClient client = new OpenAiModelsClient(mapper);
            assertThat(client.listModelIds("http://127.0.0.1:" + port + "/v1", "sk-test"))
                    .containsExactly("qwen3-plus", "qwen-max");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void listModelIds_unauthorized() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            OpenAiModelsClient client = new OpenAiModelsClient(mapper);
            assertThatThrownBy(() -> client.listModelIds("http://127.0.0.1:" + port + "/v1", "bad"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("401");
        } finally {
            server.stop(0);
        }
    }
}
