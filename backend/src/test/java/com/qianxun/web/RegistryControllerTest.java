package com.qianxun.web;

import com.qianxun.domain.AgentRegistryItem;
import com.qianxun.domain.DatasetRegistryItem;
import com.qianxun.domain.ModelRegistryItem;
import com.qianxun.repo.AgentRegistryRepository;
import com.qianxun.repo.DatasetRegistryRepository;
import com.qianxun.repo.ModelRegistryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RegistryController.class)
class RegistryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentRegistryRepository agentRepository;

    @MockBean
    private ModelRegistryRepository modelRepository;

    @MockBean
    private DatasetRegistryRepository datasetRepository;

    private static Instant now() {
        return Instant.parse("2026-01-01T00:00:00Z");
    }

    @Test
    void listEndpoints_shouldReturnData() throws Exception {
        when(agentRepository.list(true)).thenReturn(List.of(
                new AgentRegistryItem("a1", "qianxun", "千寻", "assistant", "", "", "m1", "", 10, true, now(), now())
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
    void upsertAgent_shouldValidateAndInsert() throws Exception {
        String invalid = """
                {"jsonArg":{"code":"x"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/registry/agents/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        when(agentRepository.findByCode("a-code")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(
                        new AgentRegistryItem("a-id", "a-code", "Agent", "assistant", "", "", "m1", "", 1, true, now(), now())
                ));
        String valid = """
                {"jsonArg":{"code":"a-code","name":"Agent","category":"assistant","modelCode":"m1"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/registry/agents/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("a-code"));
        verify(agentRepository, times(1)).insert(any(AgentRegistryItem.class));
        verify(agentRepository, never()).updateByCode(any(AgentRegistryItem.class));
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
}

