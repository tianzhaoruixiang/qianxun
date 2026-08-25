package com.qianxun.service;

import com.qianxun.domain.ModelRegistryItem;
import com.qianxun.llm.HermesAgentClient;
import com.qianxun.repo.ModelRegistryRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextWindowResolverTest {

    @Mock
    private ModelRegistryRepository modelRegistryRepository;
    @Mock
    private HermesAgentClient hermesAgentClient;

    @InjectMocks
    private ContextWindowResolver resolver;

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

    private static ModelRegistryItem model(String code, String name, int window) {
        Instant now = Instant.now();
        return new ModelRegistryItem("id", code, name, "openai", "", window, 1024, true, now, now);
    }
}
