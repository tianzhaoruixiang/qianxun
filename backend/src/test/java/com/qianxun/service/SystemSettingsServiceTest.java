package com.qianxun.service;

import com.qianxun.config.QianxunProperties;
import com.qianxun.context.UserContext;
import com.qianxun.llm.OpenAiModelsClient;
import com.qianxun.repo.UiConfigRepository;
import com.qianxun.web.dto.ListOpenAiModelsRequest;
import com.qianxun.security.UserRoles;
import com.qianxun.web.dto.UpdateSystemSettingsRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceTest {

    @Mock
    private UiConfigRepository ui;

    @Mock
    private OpenAiModelsClient openAiModelsClient;

    private SystemSettingsService service;

    @BeforeEach
    void setUp() {
        QianxunProperties props = new QianxunProperties();
        props.getClaude().setChatModel("qwen3.8-plus");
        props.getClaude().setOpenaiUpstreamBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        props.getClaude().setOpenaiUpstreamApiKey("sk-env-key-12345678");
        service = new SystemSettingsService(ui, props, openAiModelsClient);
        UserContext.set("1", "admin", "管理员", UserRoles.ADMIN);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void resolvedClaudeChatModel_prefersUiOverEnv() {
        when(ui.findValue(SystemSettingsService.KEY_CLAUDE_CHAT_MODEL)).thenReturn(Optional.of("DeepseekV4Flash"));
        assertThat(service.resolvedClaudeChatModel()).isEqualTo("DeepseekV4Flash");
    }

    @Test
    void resolvedClaudeChatModel_mapsGatewayAliasToEnv() {
        when(ui.findValue(SystemSettingsService.KEY_CLAUDE_CHAT_MODEL)).thenReturn(Optional.of("openai-default"));
        assertThat(service.resolvedClaudeChatModel()).isEqualTo("qwen3.8-plus");
    }

    @Test
    void resolvedOpenai_prefersUiThenEnv() {
        when(ui.findValue(SystemSettingsService.KEY_OPENAI_BASE_URL))
                .thenReturn(Optional.of("http://127.0.0.1:8000/v1"));
        when(ui.findValue(SystemSettingsService.KEY_OPENAI_API_KEY)).thenReturn(Optional.of("sk-ui"));
        assertThat(service.resolvedOpenaiBaseUrl()).isEqualTo("http://host.docker.internal:8000/v1");
        assertThat(service.resolvedOpenaiApiKey()).isEqualTo("sk-ui");
    }

    @Test
    void snapshot_masksApiKeyForAdmin() {
        when(ui.findValue(SystemSettingsService.KEY_SYSTEM_NAME)).thenReturn(Optional.of("内网助手"));
        when(ui.findValue(SystemSettingsService.KEY_CLAUDE_CHAT_MODEL)).thenReturn(Optional.of("qwen3.8-plus"));
        when(ui.findValue(SystemSettingsService.KEY_OPENAI_BASE_URL)).thenReturn(Optional.empty());
        when(ui.findValue(SystemSettingsService.KEY_OPENAI_API_KEY)).thenReturn(Optional.empty());
        var out = service.snapshot();
        assertThat(out.openaiBaseUrl()).isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1");
        assertThat(out.openaiApiKeyConfigured()).isTrue();
        assertThat(out.openaiApiKeyMasked()).startsWith("sk-e").contains("********");
        assertThat(out.openaiApiKeyMasked()).doesNotContain("sk-env-key-12345678");
        assertThat(out.claudeChatContextWindow()).isNull();
    }

    @Test
    void update_writesNameModelAndOpenAi() {
        when(ui.findValue(SystemSettingsService.KEY_SYSTEM_NAME)).thenReturn(Optional.of("内网助手"));
        when(ui.findValue(SystemSettingsService.KEY_CLAUDE_CHAT_MODEL)).thenReturn(Optional.of("qwen3.8-plus"));
        when(ui.findValue(SystemSettingsService.KEY_OPENAI_BASE_URL))
                .thenReturn(Optional.of("https://example.com/v1"));
        when(ui.findValue(SystemSettingsService.KEY_OPENAI_API_KEY)).thenReturn(Optional.of("sk-new-key-aaaa"));
        var out = service.update(new UpdateSystemSettingsRequest(
                "内网助手",
                "openai/qwen3.8-plus",
                "https://example.com/v1/",
                "sk-new-key-aaaa"
        ));
        verify(ui).upsert(SystemSettingsService.KEY_SYSTEM_NAME, "内网助手");
        verify(ui).upsert(SystemSettingsService.KEY_CLAUDE_CHAT_MODEL, "qwen3.8-plus");
        verify(ui).upsert(SystemSettingsService.KEY_OPENAI_BASE_URL, "https://example.com/v1");
        verify(ui).upsert(SystemSettingsService.KEY_OPENAI_API_KEY, "sk-new-key-aaaa");
        assertThat(out.systemName()).isEqualTo("内网助手");
        assertThat(out.claudeChatModel()).isEqualTo("qwen3.8-plus");
    }

    @Test
    void update_blankApiKeyKeepsStored() {
        when(ui.findValue(SystemSettingsService.KEY_SYSTEM_NAME)).thenReturn(Optional.of("内网助手"));
        when(ui.findValue(SystemSettingsService.KEY_CLAUDE_CHAT_MODEL)).thenReturn(Optional.of("qwen3.8-plus"));
        when(ui.findValue(SystemSettingsService.KEY_OPENAI_BASE_URL)).thenReturn(Optional.empty());
        when(ui.findValue(SystemSettingsService.KEY_OPENAI_API_KEY)).thenReturn(Optional.empty());
        service.update(new UpdateSystemSettingsRequest("内网助手", "openai-default", "https://x.example/v1", ""));
        verify(ui).upsert(SystemSettingsService.KEY_CLAUDE_CHAT_MODEL, "qwen3.8-plus");
        verify(ui, never()).upsert(org.mockito.ArgumentMatchers.eq(SystemSettingsService.KEY_OPENAI_API_KEY),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listUpstreamModels_usesFormUrlAndStoredKey() {
        when(ui.findValue(SystemSettingsService.KEY_OPENAI_API_KEY)).thenReturn(Optional.empty());
        when(openAiModelsClient.listModelInfos(
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "sk-env-key-12345678"
        )).thenReturn(List.of(
                new OpenAiModelsClient.OpenAiModelInfo("qwen3-plus", 131072),
                new OpenAiModelsClient.OpenAiModelInfo("qwen-max", 0)
        ));
        var out = service.listUpstreamModels(new ListOpenAiModelsRequest(
                "https://dashscope.aliyuncs.com/compatible-mode/v1/",
                ""
        ));
        assertThat(out.models()).containsExactly("qwen3-plus", "qwen-max");
        assertThat(out.items()).hasSize(2);
        assertThat(out.items().get(0).contextWindow()).isEqualTo(131072);
        assertThat(out.items().get(1).contextWindow()).isNull();
    }

    @Test
    void listUpstreamModels_rejectsNonAdmin() {
        UserContext.set("2", "u", "用户", UserRoles.FUNCTIONAL);
        assertThatThrownBy(() -> service.listUpstreamModels(new ListOpenAiModelsRequest("", "")))
                .isInstanceOf(ResponseStatusException.class);
    }
}
