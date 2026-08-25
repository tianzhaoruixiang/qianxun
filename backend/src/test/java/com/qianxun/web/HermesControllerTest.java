package com.qianxun.web;

import com.qianxun.llm.HermesAgentClient;
import com.qianxun.security.JwtService;
import com.qianxun.service.ContextWindowResolver;
import com.qianxun.service.HermesLiveTranscriptService;
import com.qianxun.service.HermesSkillService;
import com.qianxun.service.HermesToolsetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HermesController.class)
@Import(JwtService.class)
@TestPropertySource(properties = "qianxun.auth.enabled=false")
class HermesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HermesAgentClient hermesAgentClient;

    @MockBean
    private HermesSkillService hermesSkillService;

    @MockBean
    private HermesToolsetService hermesToolsetService;

    @MockBean
    private HermesLiveTranscriptService hermesLiveTranscriptService;

    @MockBean
    private ContextWindowResolver contextWindowResolver;

    @Test
    void listSkills_shouldReturnWrappedData() throws Exception {
        when(hermesAgentClient.isConfigured()).thenReturn(true);
        when(hermesSkillService.list("worker")).thenReturn(List.of(
                new HermesAgentClient.SkillInfo("demo", "desc", "ai", true, "agent")
        ));
        mockMvc.perform(post("/QianXunService/hermes/skills/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonArg\":{\"profile\":\"worker\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("demo"))
                .andExpect(jsonPath("$.data[0].enabled").value(true));
    }

    @Test
    void tree_shouldRequireName() throws Exception {
        mockMvc.perform(post("/QianXunService/hermes/skills/tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonArg\":{\"profile\":\"worker\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void readFile_shouldReturnContent() throws Exception {
        when(hermesSkillService.readFile("worker", "demo", "SKILL.md")).thenReturn(
                new HermesSkillService.FileBody(true, "SKILL.md", "# hi", true, "# hi".getBytes(StandardCharsets.UTF_8), "")
        );
        mockMvc.perform(post("/QianXunService/hermes/skills/file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonArg\":{\"profile\":\"worker\",\"name\":\"demo\",\"path\":\"SKILL.md\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").value("# hi"));
    }

    @Test
    void updateFile_shouldSave() throws Exception {
        when(hermesSkillService.saveFile(eq("worker"), eq("demo"), eq("SKILL.md"), anyString()))
                .thenReturn(new HermesAgentClient.ManagedWriteResult(true, "SKILL.md", ""));
        mockMvc.perform(post("/QianXunService/hermes/skills/file/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonArg\":{\"profile\":\"worker\",\"name\":\"demo\",\"path\":\"SKILL.md\",\"content\":\"# x\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void uploadZip_shouldRejectNonZip() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());
        mockMvc.perform(multipart("/QianXunService/hermes/skills/upload").file(file).param("profile", "worker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void uploadZip_shouldReturnInstalled() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "demo.zip", "application/zip", "PK".getBytes());
        when(hermesSkillService.uploadZip(eq("worker"), eq("demo.zip"), any()))
                .thenReturn(new HermesSkillService.UploadResult(true, List.of("demo"), List.of()));
        mockMvc.perform(multipart("/QianXunService/hermes/skills/upload").file(file).param("profile", "worker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.installed[0]").value("demo"));
    }

    @Test
    void downloadZip_shouldAttachFile() throws Exception {
        when(hermesSkillService.downloadZip("worker", "demo")).thenReturn(
                new HermesSkillService.ZipDownload(true, "ZIP".getBytes(StandardCharsets.UTF_8), "demo.zip", "")
        );
        mockMvc.perform(get("/QianXunService/hermes/skills/download")
                        .param("profile", "worker")
                        .param("name", "demo"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"));
    }

    @Test
    void listToolsets_whenHermesOff_shouldReturnEmpty() throws Exception {
        when(hermesAgentClient.isConfigured()).thenReturn(false);
        mockMvc.perform(post("/QianXunService/hermes/tools/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonArg\":{\"profile\":\"default\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void listToolsets_shouldReturnWrappedData() throws Exception {
        when(hermesAgentClient.isConfigured()).thenReturn(true);
        when(hermesToolsetService.list("worker")).thenReturn(List.of(
                new HermesToolsetService.ToolsetView(
                        "web", "Web", "搜索与提取", "cli", "CLI", true, true,
                        List.of(new HermesToolsetService.ToolItem("WebSearch", "网页搜索", "search", true))
                )
        ));
        mockMvc.perform(post("/QianXunService/hermes/tools/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonArg\":{\"profile\":\"worker\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("web"))
                .andExpect(jsonPath("$.data[0].tools[0].displayName").value("网页搜索"))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[0].tools[0].enabled").value(true));
    }

    @Test
    void toggleToolset_shouldRequireName() throws Exception {
        mockMvc.perform(post("/QianXunService/hermes/tools/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonArg\":{\"profile\":\"worker\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void toggleToolset_shouldReturnEnabled() throws Exception {
        when(hermesToolsetService.toggle("worker", "web", false)).thenReturn(
                new HermesAgentClient.ToolsetWriteResult(true, "web", false, "")
        );
        mockMvc.perform(post("/QianXunService/hermes/tools/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonArg\":{\"profile\":\"worker\",\"name\":\"web\",\"enabled\":false}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }
}
