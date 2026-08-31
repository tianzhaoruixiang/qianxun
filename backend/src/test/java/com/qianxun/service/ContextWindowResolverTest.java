package com.qianxun.service;

import com.qianxun.domain.ModelRegistryItem;
import com.qianxun.llm.HermesAgentClient;
import com.qianxun.llm.OpenAiModelsClient;
import com.qianxun.repo.ModelRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextWindowResolverTest {

    @Mock
    private ModelRegistryRepository modelRegistryRepository;
    @Mock
    private HermesAgentClient hermesAgentClient;
    @Mock
    private OpenAiModelsClient openAiModelsClient;
    @Mock
    private SystemSettingsService systemSettingsService;

    @InjectMocks
    private ContextWindowResolver resolver;

    @BeforeEach
    void defaults() {
        lenient().when(systemSettingsService.resolvedOpenaiBaseUrl()).thenReturn("");
        lenient().when(systemSettingsService.resolvedOpenaiApiKey()).thenReturn("");
        lenient().when(systemSettingsService.resolvedClaudeChatModel()).thenReturn("");
        lenient().when(openAiModelsClient.findContextWindow(anyString(), anyString(), anyString())).thenReturn(0);
    }

    @Test
    void unknown_shouldReturnZeroNot128k() {
        when(modelRegistryRepository.findByCode("missing")).thenReturn(Optional.empty());
        when(modelRegistryRepository.findByNameIgnoreCase("missing")).thenReturn(Optional.empty());
        when(hermesAgentClient.isConfigured()).thenReturn(false);
        assertThat(resolver.resolve("missing", null, "default")).isZero();
    }

    @Test
    void shouldUseModelRegistryByCode() {
        when(modelRegistryRepository.findByCode("qwen")).thenReturn(Optional.of(model("qwen", "通义", 32768)));
        assertThat(resolver.resolve("qwen", "other", "default")).isEqualTo(32768);
    }

    @Test
    void shouldPreferLiveUpstreamWindowOverRegistry() {
        when(systemSettingsService.resolvedOpenaiBaseUrl()).thenReturn("http://litellm:4000/v1");
        when(systemSettingsService.resolvedOpenaiApiKey()).thenReturn("sk");
        when(systemSettingsService.resolvedClaudeChatModel()).thenReturn("qwen3.6-plus");
        when(openAiModelsClient.findContextWindow(eq("http://litellm:4000/v1"), eq("sk"), eq("qwen")))
                .thenReturn(131072);
        assertThat(resolver.resolve("qwen", "qwen3.6-plus", "default")).isEqualTo(131072);
    }

    @Test
    void shouldUseHermesProfileWindow() {
        when(modelRegistryRepository.findByCode("x")).thenReturn(Optional.empty());
        when(modelRegistryRepository.findByNameIgnoreCase("x")).thenReturn(Optional.empty());
        when(hermesAgentClient.isConfigured()).thenReturn(true);
        when(hermesAgentClient.listProfiles(any())).thenReturn(List.of(
                new HermesAgentClient.HermesProfile("worker", "", "gpt", false, "", 64000)
        ));
        assertThat(resolver.resolve("x", null, "worker")).isEqualTo(64000);
    }

    @Test
    void shouldMapHermesModelToRegistryName() {
        when(modelRegistryRepository.findByCode("sel")).thenReturn(Optional.empty());
        when(modelRegistryRepository.findByNameIgnoreCase("sel")).thenReturn(Optional.empty());
        when(hermesAgentClient.isConfigured()).thenReturn(true);
        when(hermesAgentClient.listProfiles(any())).thenReturn(List.of(
                new HermesAgentClient.HermesProfile("default", "", "DeepSeek-V3", true, "", null)
        ));
        when(modelRegistryRepository.findByCode("DeepSeek-V3")).thenReturn(Optional.empty());
        when(modelRegistryRepository.findByNameIgnoreCase("DeepSeek-V3")).thenReturn(Optional.of(model("ds", "DeepSeek-V3", 131072)));
        assertThat(resolver.resolve("sel", null, "default")).isEqualTo(131072);
    }

    @Test
    void stubHermesAgent_shouldPreferUpstreamModelWindow() {
        when(modelRegistryRepository.findByCode("DeepSeek-V3")).thenReturn(Optional.empty());
        when(modelRegistryRepository.findByNameIgnoreCase("DeepSeek-V3")).thenReturn(Optional.of(model("ds", "DeepSeek-V3", 65536)));
        assertThat(resolver.resolve("hermes-agent", "DeepSeek-V3", "worker")).isEqualTo(65536);
    }

    @Test
    void enrichHermesProfileWindow_shouldFallbackToModelRegistry() {
        when(modelRegistryRepository.findByCode("gpt")).thenReturn(Optional.of(model("gpt", "GPT", 32000)));
        assertThat(resolver.enrichHermesProfileWindow(null, "gpt")).isEqualTo(32000);
        assertThat(resolver.enrichHermesProfileWindow(64000, "gpt")).isEqualTo(64000);
    }

    @Test
    void resolveRuntimeModelWindow_usesConfiguredChatModel() {
        when(systemSettingsService.resolvedClaudeChatModel()).thenReturn("qwen3.6-plus");
        when(systemSettingsService.resolvedOpenaiBaseUrl()).thenReturn("http://litellm:4000/v1");
        when(systemSettingsService.resolvedOpenaiApiKey()).thenReturn("sk");
        when(openAiModelsClient.findContextWindow(eq("http://litellm:4000/v1"), eq("sk"), eq("qwen3.6-plus")))
                .thenReturn(131072);
        assertThat(resolver.resolveRuntimeModelWindow()).isEqualTo(131072);
    }

    @Test
    void resolveRuntimeModelWindow_usesKnownCatalogWhenUpstreamOmitsWindow() {
        when(systemSettingsService.resolvedClaudeChatModel()).thenReturn("qwen3.6-plus");
        lenient().when(modelRegistryRepository.findByCode("qwen3.6-plus")).thenReturn(Optional.empty());
        lenient().when(modelRegistryRepository.findByNameIgnoreCase("qwen3.6-plus")).thenReturn(Optional.empty());
        assertThat(resolver.resolveRuntimeModelWindow()).isEqualTo(1_000_000);
    }

    private static ModelRegistryItem model(String code, String name, int window) {
        Instant now = Instant.now();
        return new ModelRegistryItem("id", code, name, "openai", "", window, 1024, true, now, now);
    }
}
