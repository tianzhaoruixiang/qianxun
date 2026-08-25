package com.qianxun.web;

import com.qianxun.context.UserContext;
import com.qianxun.domain.AgentRegistryItem;
import com.qianxun.domain.DatasetRegistryItem;
import com.qianxun.domain.ModelRegistryItem;
import com.qianxun.repo.AgentRegistryRepository;
import com.qianxun.repo.DatasetRegistryRepository;
import com.qianxun.repo.ModelRegistryRepository;
import com.qianxun.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RegistryController.class)
@Import(JwtService.class)
@TestPropertySource(properties = "qianxun.auth.enabled=false")
class RegistryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentRegistryRepository agentRepository;

    @MockBean
    private ModelRegistryRepository modelRepository;

    @MockBean
    private DatasetRegistryRepository datasetRepository;

    @MockBean
    private com.qianxun.llm.HermesAgentClient hermesAgentClient;

    @MockBean
    private com.qianxun.service.QianXunServiceChatSession sessionService;

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @BeforeEach
    void stubHermesDefaults() {
        lenient().when(hermesAgentClient.createProfile(any(), any(), any()))
                .thenReturn(new com.qianxun.llm.HermesAgentClient.CreateProfileResult(
                        true, "stub", "/opt/data/stub/profiles/stub", "ok", false));
        lenient().when(hermesAgentClient.putSoul(any(), any(), any()))
                .thenReturn(new com.qianxun.llm.HermesAgentClient.SoulResult(true, "stub", true, "ok"));
        lenient().when(hermesAgentClient.publishProfileTemplate(any(), any()))
                .thenReturn(new com.qianxun.llm.HermesAgentClient.PublishTemplateResult(
                        true, "stub", "/opt/data/_templates/profiles/stub", "ok"));
    }

    private static Instant now() {
        return Instant.parse("2026-01-01T00:00:00Z");
    }

    @Test
    void listEndpoints_shouldReturnData() throws Exception {
        when(agentRepository.list(true)).thenReturn(List.of(
                new AgentRegistryItem("a1", "qianxun", "千寻", "assistant", "", "", "m1", "", "", "", "", "", "", "", "", "", 10, true, now(), now())
        ));
        when(modelRepository.list(true)).thenReturn(List.of(
                new ModelRegistryItem("m1", "moonshot-k2", "Moonshot", "kimi-coding", "https://api.moonshot.cn/v1", 262144, 16384, true, now(), now())
        ));
        when(datasetRepository.list(true)).thenReturn(List.of(
                new DatasetRegistryItem("d1", "ds-default", "默认数据集", "", "tidb", "table://x", 10, true, now(), now())
        ));

        mockMvc.perform(post("/QianXunService/registry/agents/list")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("qianxun"));

        mockMvc.perform(post("/QianXunService/registry/models/list")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].provider").value("kimi-coding"));

        mockMvc.perform(post("/QianXunService/registry/datasets/list")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("ds-default"));
    }

    @Test
    void listEndpoints_shouldRespectEnabledOnlyFalse() throws Exception {
        when(agentRepository.list(false)).thenReturn(List.of());
        when(modelRepository.list(false)).thenReturn(List.of());
        when(datasetRepository.list(false)).thenReturn(List.of());
        String req = """
                {"jsonArg":{"enabledOnly":false},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/registry/agents/list")
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/QianXunService/registry/models/list")
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/QianXunService/registry/datasets/list")
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void upsertAndDeleteAgent_shouldForbidFunctionalUser() throws Exception {
        mockMvc.perform(post("/QianXunService/registry/agents/upsert")
                        .header(UserContext.HEADER_USER_ID, "u2")
                        .header(UserContext.HEADER_USER_NAME, "operator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonArg":{"code":"a","name":"A","soulMd":"soul"},"generalArgument":{"userId":"u2","loginName":"operator","ip":"","coralKey":""}}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(post("/QianXunService/registry/agents/delete")
                        .header(UserContext.HEADER_USER_ID, "u2")
                        .header(UserContext.HEADER_USER_NAME, "operator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonArg":{"code":"a"},"generalArgument":{"userId":"u2","loginName":"operator","ip":"","coralKey":""}}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(agentRepository, never()).insert(any());
        verify(agentRepository, never()).deleteByCode(any());
    }

    @Test
    void upsertAgent_shouldValidateAndInsert() throws Exception {
        String invalid = """
                {"jsonArg":{"code":"x"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/registry/agents/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        stubHermesCreateAndSoul("a-code", "soul");
        when(agentRepository.findByCode("a-code")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(
                        new AgentRegistryItem("a-id", "a-code", "Agent", "assistant", "", "", "m1", "", "", "", "", "", "", "", "", "", 1, true, now(), now())
                ));
        String valid = """
                {"jsonArg":{"code":"a-code","name":"Agent","category":"assistant","modelCode":"m1","soulMd":"soul"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/registry/agents/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("a-code"));
        verify(agentRepository, times(1)).insert(any(AgentRegistryItem.class));
        verify(agentRepository, never()).updateByCode(any(AgentRegistryItem.class));
        verify(hermesAgentClient).createProfile(eq(UserContext.DEFAULT_USER_ID), eq("a-code"), any());
        verify(hermesAgentClient).putSoul(eq(UserContext.DEFAULT_USER_ID), eq("a-code"), eq("soul"));
        verify(hermesAgentClient).publishProfileTemplate(eq(UserContext.DEFAULT_USER_ID), eq("a-code"));
    }

    @Test
    void upsertAgent_shouldRejectBlankSoulMd() throws Exception {
        String blankSoul = """
                {"jsonArg":{"code":"a-code","name":"Agent","soulMd":"  "},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/registry/agents/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankSoul))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("soulMd 不能为空"));
        verify(hermesAgentClient, never()).createProfile(any(), any(), any());
        verify(agentRepository, never()).insert(any(AgentRegistryItem.class));
    }

    @Test
    void upsertAgent_shouldAlwaysRegisterHermesProfile() throws Exception {
        stubHermesCreateAndSoul("a-code", "persona");
        when(agentRepository.findByCode("a-code")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(
                        new AgentRegistryItem("a-id", "a-code", "Agent", "assistant", "", "", "m1",
                                "", "", "", "", "", "", "", "", "a-code", 1, true, now(), now())
                ));
        String valid = """
                {"jsonArg":{"code":"a-code","name":"Agent","soulMd":"persona"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/registry/agents/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hermesProfile").value("a-code"));
        verify(hermesAgentClient).createProfile(eq(UserContext.DEFAULT_USER_ID), eq("a-code"), any());
        verify(hermesAgentClient).putSoul(eq(UserContext.DEFAULT_USER_ID), eq("a-code"), eq("persona"));
        verify(hermesAgentClient).publishProfileTemplate(eq(UserContext.DEFAULT_USER_ID), eq("a-code"));
        verify(agentRepository, times(1)).insert(any(AgentRegistryItem.class));
    }

    @Test
    void upsertAgent_shouldWriteSoulMdWhenProvided() throws Exception {
        stubHermesCreateAndSoul("a-code", "# Soul\nBe helpful.");
        when(agentRepository.findByCode("a-code")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(
                        new AgentRegistryItem("a-id", "a-code", "Agent", "assistant", "", "", "m1",
                                "", "", "", "", "", "", "", "", "a-code", 1, true, now(), now())
                ));
        String valid = """
                {"jsonArg":{"code":"a-code","name":"Agent","soulMd":"# Soul\\nBe helpful."},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/registry/agents/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(hermesAgentClient).putSoul(eq(UserContext.DEFAULT_USER_ID), eq("a-code"), eq("# Soul\nBe helpful."));
        verify(hermesAgentClient).publishProfileTemplate(eq(UserContext.DEFAULT_USER_ID), eq("a-code"));
        verify(agentRepository, times(1)).insert(any(AgentRegistryItem.class));
    }

    @Test
    void upsertAgent_shouldFailWhenSoulWriteFails() throws Exception {
        when(hermesAgentClient.createProfile(eq(UserContext.DEFAULT_USER_ID), eq("a-code"), any()))
                .thenReturn(new com.qianxun.llm.HermesAgentClient.CreateProfileResult(
                        true, "a-code", "/opt/data/profiles/a-code", "ok", false));
        when(hermesAgentClient.putSoul(eq(UserContext.DEFAULT_USER_ID), eq("a-code"), eq("persona")))
                .thenReturn(new com.qianxun.llm.HermesAgentClient.SoulResult(
                        false, "", false, "HTTP 404"));
        when(agentRepository.findByCode("a-code")).thenReturn(Optional.empty());
        String valid = """
                {"jsonArg":{"code":"a-code","name":"Agent","soulMd":"persona"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/registry/agents/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(502));
        verify(agentRepository, never()).insert(any(AgentRegistryItem.class));
    }

    @Test
    void upsertAgent_shouldRegisterHermesEvenWhenProfileSet() throws Exception {
        stubHermesCreateAndSoul("exist-p", "hi");
        when(agentRepository.findByCode("a-code")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(
                        new AgentRegistryItem("a-id", "a-code", "Agent", "assistant", "", "", "",
                                "", "", "", "", "", "", "", "", "exist-p", 1, true, now(), now())
                ));
        String valid = """
                {"jsonArg":{"code":"a-code","name":"Agent","hermesProfile":"exist-p","soulMd":"hi"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/registry/agents/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(hermesAgentClient).createProfile(eq(UserContext.DEFAULT_USER_ID), eq("exist-p"), any());
        verify(hermesAgentClient).putSoul(eq(UserContext.DEFAULT_USER_ID), eq("exist-p"), eq("hi"));
        verify(hermesAgentClient).publishProfileTemplate(eq(UserContext.DEFAULT_USER_ID), eq("exist-p"));
        verify(agentRepository, times(1)).insert(any(AgentRegistryItem.class));
    }

    @Test
    void upsertAgent_shouldFailWhenHermesRegisterFails() throws Exception {
        when(hermesAgentClient.createProfile(any(), any(), any()))
                .thenReturn(new com.qianxun.llm.HermesAgentClient.CreateProfileResult(
                        false, "a-code", "", "HTTP 404", false));
        when(agentRepository.findByCode("a-code")).thenReturn(Optional.empty());
        String valid = """
                {"jsonArg":{"code":"a-code","name":"Agent","soulMd":"persona"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/registry/agents/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(502));
        verify(agentRepository, never()).insert(any(AgentRegistryItem.class));
    }

    @Test
    void upsertAgent_shouldIgnoreLegacyOpenAiFields() throws Exception {
        stubHermesCreateAndSoul("a-code", "soul");
        when(agentRepository.findByCode("a-code")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(
                        new AgentRegistryItem("a-id", "a-code", "Agent", "assistant", "", "", "m1",
                                "", "", "", "", "", "", "", "", "a-code", 1, true, now(), now())
                ));
        String valid = """
                {"jsonArg":{"code":"a-code","name":"Agent","soulMd":"soul","apiBaseUrl":"https://api.openai.com/v1","upstreamModel":"gpt-4o","apiKey":"sk-test"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/registry/agents/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(agentRepository).insert(org.mockito.ArgumentMatchers.argThat(item ->
                "".equals(item.apiBaseUrl())
                        && "".equals(item.upstreamModel())
                        && "".equals(item.apiKey())
                        && "a-code".equals(item.hermesProfile())
        ));
    }

    @Test
    void deleteAgent_shouldValidateAndCallRepository() throws Exception {
        mockMvc.perform(post("/QianXunService/registry/agents/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonArg\":{},\"generalArgument\":{\"userId\":\"1\",\"loginName\":\"u\",\"ip\":\"\",\"coralKey\":\"\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        when(agentRepository.findByCode("missing")).thenReturn(Optional.empty());
        mockMvc.perform(post("/QianXunService/registry/agents/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonArg":{"code":"missing"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
        verify(agentRepository, never()).deleteByCode("missing");
        verify(hermesAgentClient, never()).deleteProfile(any(), any());

        when(agentRepository.findByCode("to-del")).thenReturn(Optional.of(
                new AgentRegistryItem("a-id", "to-del", "Agent", "assistant", "", "", "",
                        "", "", "", "", "", "", "", "", "", 1, true, now(), now())
        ));
        when(agentRepository.deleteByCode("to-del")).thenReturn(1);
        mockMvc.perform(post("/QianXunService/registry/agents/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonArg":{"code":"to-del"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(agentRepository).deleteByCode("to-del");
        verify(hermesAgentClient, never()).deleteProfile(any(), any());
        verify(sessionService).deleteByAgent("to-del", "");
    }

    @Test
    void deleteAgent_shouldDeleteHermesProfile() throws Exception {
        when(agentRepository.findByCode("to-del")).thenReturn(Optional.of(
                new AgentRegistryItem("a-id", "to-del", "Agent", "assistant", "", "", "",
                        "", "", "", "", "", "", "", "", "p-one", 1, true, now(), now())
        ));
        when(hermesAgentClient.deleteProfile(eq(UserContext.DEFAULT_USER_ID), eq("p-one")))
                .thenReturn(new com.qianxun.llm.HermesAgentClient.DeleteProfileResult(
                        true, "p-one", false, "ok"));
        when(agentRepository.deleteByCode("to-del")).thenReturn(1);
        mockMvc.perform(post("/QianXunService/registry/agents/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonArg":{"code":"to-del"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(hermesAgentClient).deleteProfile(eq(UserContext.DEFAULT_USER_ID), eq("p-one"));
        verify(sessionService).deleteByAgent("to-del", "p-one");
        verify(agentRepository).deleteByCode("to-del");
    }

    @Test
    void deleteAgent_shouldFailWhenHermesDeleteFails() throws Exception {
        when(agentRepository.findByCode("to-del")).thenReturn(Optional.of(
                new AgentRegistryItem("a-id", "to-del", "Agent", "assistant", "", "", "",
                        "", "", "", "", "", "", "", "", "p-one", 1, true, now(), now())
        ));
        when(hermesAgentClient.deleteProfile(eq(UserContext.DEFAULT_USER_ID), eq("p-one")))
                .thenReturn(new com.qianxun.llm.HermesAgentClient.DeleteProfileResult(
                        false, "p-one", false, "HTTP 500"));
        mockMvc.perform(post("/QianXunService/registry/agents/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonArg":{"code":"to-del"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(502));
        verify(agentRepository, never()).deleteByCode(any());
        verify(sessionService, never()).deleteByAgent(any(), any());
    }

    @Test
    void upsertModel_shouldUpdateWhenExists() throws Exception {
        when(modelRepository.findByCode("m-code")).thenReturn(Optional.of(
                        new ModelRegistryItem("m-id", "m-code", "Old", "p", "u", 1, 1, true, now(), now()))
                ).thenReturn(Optional.of(
                        new ModelRegistryItem("m-id", "m-code", "New", "p2", "u2", 2, 2, true, now(), now())
                ));

        String req = """
                {"jsonArg":{"code":"m-code","name":"New","provider":"p2","baseUrl":"u2","contextWindow":2,"maxTokens":2},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/registry/models/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("m-code"));

        verify(modelRepository, times(1)).updateByCode(any(ModelRegistryItem.class));
        verify(modelRepository, never()).insert(any(ModelRegistryItem.class));
    }

    @Test
    void upsertModel_shouldInsertWhenMissing() throws Exception {
        when(modelRepository.findByCode("m-new")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(
                        new ModelRegistryItem("mid2", "m-new", "MNew", "openai-compatible", "", 128000, 1024, true, now(), now())
                ));
        String req = """
                {"jsonArg":{"code":"m-new","name":"MNew"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/registry/models/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("m-new"));
        verify(modelRepository, times(1)).insert(any(ModelRegistryItem.class));
    }

    @Test
    void upsertDataset_shouldValidateAndUpdate() throws Exception {
        String invalid = """
                {"jsonArg":{"name":"NoCode"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/registry/datasets/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        when(datasetRepository.findByCode("ds1")).thenReturn(Optional.of(
                        new DatasetRegistryItem("d-id", "ds1", "old", "", "tidb", "", 1, true, now(), now()))
                ).thenReturn(Optional.of(
                        new DatasetRegistryItem("d-id", "ds1", "new", "", "tidb", "", 2, true, now(), now())
                ));
        String req = """
                {"jsonArg":{"code":"ds1","name":"new","sourceType":"tidb","docCount":2},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/registry/datasets/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("ds1"));

        verify(datasetRepository, times(1)).updateByCode(any(DatasetRegistryItem.class));
        verify(datasetRepository, never()).insert(any(DatasetRegistryItem.class));
        verify(datasetRepository, times(2)).findByCode(eq("ds1"));
    }

    @Test
    void upsertDataset_shouldInsertWhenMissing() throws Exception {
        when(datasetRepository.findByCode("ds-new")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(
                        new DatasetRegistryItem("did2", "ds-new", "new", "", "mixed", "", 0, true, now(), now())
                ));
        String req = """
                {"jsonArg":{"code":"ds-new","name":"new"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/registry/datasets/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("ds-new"));
        verify(datasetRepository, times(1)).insert(any(DatasetRegistryItem.class));
    }

    private void stubHermesCreateAndSoul(String profile, String soul) {
        when(hermesAgentClient.createProfile(eq(UserContext.DEFAULT_USER_ID), eq(profile), any()))
                .thenReturn(new com.qianxun.llm.HermesAgentClient.CreateProfileResult(
                        true, profile, "/opt/data/profiles/" + profile, "ok", false));
        when(hermesAgentClient.putSoul(eq(UserContext.DEFAULT_USER_ID), eq(profile), eq(soul)))
                .thenReturn(new com.qianxun.llm.HermesAgentClient.SoulResult(true, soul, true, "ok"));
        when(hermesAgentClient.publishProfileTemplate(eq(UserContext.DEFAULT_USER_ID), eq(profile)))
                .thenReturn(new com.qianxun.llm.HermesAgentClient.PublishTemplateResult(
                        true, profile, "/opt/data/_templates/profiles/" + profile, "ok"));
    }
}

