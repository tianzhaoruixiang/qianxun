package com.qianxun.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.domain.DataFile;
import com.qianxun.domain.DataPortraitPoint;
import com.qianxun.repo.DataFileRepository;
import com.qianxun.repo.DataPortraitRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DataController.class)
class DataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DataFileRepository fileRepository;

    @MockBean
    private DataPortraitRepository portraitRepository;

    @Test
    void listFiles_shouldReturnWrappedData() throws Exception {
        when(fileRepository.listOrderByDateDesc(200)).thenReturn(List.of(
                new DataFile("f1", "doc.docx", "2026-08-12", DataFile.KIND_WORD, Instant.now(), Instant.now())
        ));
        mockMvc.perform(post("/QianXunService/data/files/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value("f1"))
                .andExpect(jsonPath("$.data[0].kind").value("word"));
    }

    @Test
    void portrait_shouldReturnEmptyWhenNoPoints() throws Exception {
        when(portraitRepository.listByGroup(DataPortraitPoint.DEFAULT_GROUP)).thenReturn(List.of());
        mockMvc.perform(post("/QianXunService/data/portrait")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.labels").isArray())
                .andExpect(jsonPath("$.data.labels.length()").value(0));
    }

    @Test
    void portrait_shouldMapPointsAndFocus() throws Exception {
        when(portraitRepository.listByGroup(DataPortraitPoint.DEFAULT_GROUP)).thenReturn(List.of(
                new DataPortraitPoint("p1", "default", "个", 0, "1月", 1, 2, false, null),
                new DataPortraitPoint("p2", "default", "个", 1, "2月", 3, 4, true, "2021.01.06")
        ));
        mockMvc.perform(post("/QianXunService/data/portrait")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.unit").value("个"))
                .andExpect(jsonPath("$.data.focusIndex").value(1))
                .andExpect(jsonPath("$.data.focusLabel").value("2021.01.06"));
    }

    @Test
    void upsertFile_shouldRejectInvalidBody() throws Exception {
        String req = """
                {"jsonArg":{"id":"x"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/files/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void upsertFile_shouldInsertWhenNotExists() throws Exception {
        when(fileRepository.findById("f100")).thenReturn(Optional.empty());
        String req = """
                {"jsonArg":{"id":"f100","name":"a.docx","date":"2026-08-12","kind":"word"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/files/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("f100"));
        verify(fileRepository, times(1)).insert(any(DataFile.class));
        verify(fileRepository, never()).update(any(DataFile.class));
    }

    @Test
    void upsertFile_shouldUpdateWhenExists() throws Exception {
        when(fileRepository.findById("f101")).thenReturn(Optional.of(
                new DataFile("f101", "old.docx", "2026-01-01", "word", Instant.now(), Instant.now())
        ));
        String req = """
                {"jsonArg":{"id":"f101","name":"new.docx","date":"2026-08-12","kind":"excel"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/files/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.kind").value("excel"));
        verify(fileRepository, times(1)).update(any(DataFile.class));
        verify(fileRepository, never()).insert(any(DataFile.class));
    }

    @Test
    void upsertPortrait_shouldRejectEmptyPoints() throws Exception {
        String req = """
                {"jsonArg":{"groupCode":"g1","unit":"个","points":[]},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/portrait/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void upsertPortrait_shouldReplaceGroupAndInsertPoints() throws Exception {
        String req = """
                {"jsonArg":{"groupCode":"g2","unit":"个","points":[
                  {"label":"1月","seriesA":1,"seriesB":2,"focused":false},
                  {"label":"2月","seriesA":3,"seriesB":4,"focused":true,"focusLabel":"focus"}
                ]},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/portrait/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.labels.length()").value(2))
                .andExpect(jsonPath("$.data.focusIndex").value(1));

        verify(portraitRepository, times(1)).deleteByGroup("g2");
        verify(portraitRepository, times(2)).insert(any(DataPortraitPoint.class));

        ArgumentCaptor<DataPortraitPoint> captor = ArgumentCaptor.forClass(DataPortraitPoint.class);
        verify(portraitRepository, times(2)).insert(captor.capture());
        List<DataPortraitPoint> saved = captor.getAllValues();
        assertThat(saved.get(0).orderIndex()).isEqualTo(0);
        assertThat(saved.get(1).focused()).isTrue();
    }
}

