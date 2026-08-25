package com.qianxun.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.domain.DataFile;
import com.qianxun.domain.DataPortraitPoint;
import com.qianxun.repo.DataFileRepository;
import com.qianxun.repo.DataPortraitRepository;
import com.qianxun.security.JwtService;
import com.qianxun.storage.MinioStorage;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DataController.class)
@Import(JwtService.class)
@TestPropertySource(properties = "qianxun.auth.enabled=false")
class DataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DataFileRepository fileRepository;

    @MockBean
    private DataPortraitRepository portraitRepository;

    @MockBean
    private MinioStorage minioStorage;

    @Test
    void listFiles_shouldReturnWrappedData() throws Exception {
        Instant now = Instant.now();
        when(fileRepository.listByUserIdOrderByDateDesc("1", 500)).thenReturn(List.of(
                DataFile.of("f1", "doc.docx", "2026-08-12", DataFile.KIND_WORD, null, null, now, now)
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
    void listFiles_shouldTruncatePreviewAndHideUploadNote() throws Exception {
        Instant now = Instant.now();
        String longBody = "正文".repeat(80);
        when(fileRepository.listByUserIdOrderByDateDesc("1", 500)).thenReturn(List.of(
                DataFile.of("f-long", "a.md", "2026-08-12", DataFile.KIND_TEXT, longBody, null, now, now),
                DataFile.of("f-note", "b.md", "2026-08-12", DataFile.KIND_TEXT, "已上传文档「b.md」（10 字节）", null, now, now)
        ));
        mockMvc.perform(post("/QianXunService/data/files/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].preview").value(org.hamcrest.Matchers.endsWith("…")));
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
        Instant t = Instant.now();
        when(fileRepository.findById("f101")).thenReturn(Optional.of(
                DataFile.of("f101", "old.docx", "2026-01-01", "word", null, null, t, t)
        ));
        String req = """
                {"jsonArg":{"id":"f101","name":"new.docx","date":"2026-08-12","kind":"excel","folderPath":"情报"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
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
    void upsertFile_shouldHideOtherUsersFile() throws Exception {
        Instant t = Instant.now();
        when(fileRepository.findById("f-x")).thenReturn(Optional.of(
                new DataFile("f-x", "99", "old.docx", "2026-01-01", "word", null, null,
                        null, null, null, null, "", t, t)
        ));
        String req = """
                {"jsonArg":{"id":"f-x","name":"new.docx","date":"2026-08-12","kind":"word"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/files/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
        verify(fileRepository, never()).update(any(DataFile.class));
    }

    @Test
    void fileDetail_shouldRejectBlankId() throws Exception {
        String req = """
                {"jsonArg":{"id":"  "},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/files/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void fileDetail_shouldHideOtherUsersFile() throws Exception {
        Instant t = Instant.now();
        when(fileRepository.findById("f2")).thenReturn(Optional.of(
                new DataFile("f2", "99", "secret.docx", "2026-01-01", "word", "txt", "[[\"a\",\"b\"]]",
                        "users/99/f2/secret.docx", "application/msword", 10L, "tok", "", t, t)
        ));
        String req = """
                {"jsonArg":{"id":"f2"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/files/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void fileDetail_shouldReturnOwnedFile() throws Exception {
        Instant t = Instant.now();
        when(fileRepository.findById("f3")).thenReturn(Optional.of(
                new DataFile("f3", "1", "a.xlsx", "2026-08-12", "excel", "body", "[[\"字段\",\"值\"],[\"文件名\",\"a.xlsx\"]]",
                        "users/1/f3/a.xlsx", "application/vnd.ms-excel", 20L, "pub3", "", t, t)
        ));
        String req = """
                {"jsonArg":{"id":"f3"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/files/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("f3"))
                .andExpect(jsonPath("$.data.kind").value("excel"))
                .andExpect(jsonPath("$.data.publicUrl").value("/QianXunService/data/files/public/pub3"));
    }

    @Test
    void fileDetail_shouldRejectNullBody() throws Exception {
        mockMvc.perform(post("/QianXunService/data/files/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void fileDetail_shouldReturnByPublicToken() throws Exception {
        Instant t = Instant.now();
        when(fileRepository.findByPublicToken("pub3")).thenReturn(Optional.of(
                new DataFile("f3", "1", "a.xlsx", "2026-08-12", "excel", "body", "[[\"字段\",\"值\"],[\"文件名\",\"a.xlsx\"]]",
                        "users/1/f3/a.xlsx", "application/vnd.ms-excel", 20L, "pub3", "", t, t)
        ));
        String req = """
                {"jsonArg":{"publicToken":"pub3"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/files/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("f3"))
                .andExpect(jsonPath("$.data.publicToken").value("pub3"));
    }

    @Test
    void uploadFile_shouldRejectEmpty() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "a.docx", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[0]);
        mockMvc.perform(multipart("/QianXunService/data/files/upload").file(empty))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void uploadFile_shouldRejectUnsupportedType() throws Exception {
        MockMultipartFile exe = new MockMultipartFile("file", "a.exe", MediaType.APPLICATION_OCTET_STREAM_VALUE, "x".getBytes());
        mockMvc.perform(multipart("/QianXunService/data/files/upload").file(exe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void uploadFile_shouldRejectWhenMinioDisabled() throws Exception {
        when(minioStorage.isEnabled()).thenReturn(false);
        MockMultipartFile doc = new MockMultipartFile("file", "a.docx", MediaType.APPLICATION_OCTET_STREAM_VALUE, "hello".getBytes());
        mockMvc.perform(multipart("/QianXunService/data/files/upload").file(doc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(503));
        verify(fileRepository, never()).insert(any(DataFile.class));
    }

    @Test
    void uploadFile_shouldExtractWordText() throws Exception {
        when(minioStorage.isEnabled()).thenReturn(true);
        when(minioStorage.putUserFile(anyString(), anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("users/1/abc/a.docx");
        MockMultipartFile doc = new MockMultipartFile(
                "file", "a.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                sampleDocx("千寻情报摘要：目标人员近期活动")
        );
        mockMvc.perform(multipart("/QianXunService/data/files/upload").file(doc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.files[0].name").value("a.docx"))
                .andExpect(jsonPath("$.data.files[0].kind").value("word"))
                .andExpect(jsonPath("$.data.files[0].preview").value(org.hamcrest.Matchers.containsString("千寻情报摘要")));
        ArgumentCaptor<DataFile> captor = ArgumentCaptor.forClass(DataFile.class);
        verify(fileRepository, times(1)).insert(captor.capture());
        assertThat(captor.getValue().detailText()).contains("千寻情报摘要");
        assertThat(captor.getValue().objectKey()).isEqualTo("users/1/abc/a.docx");
    }

    @Test
    void uploadFile_shouldExtractExcelCells() throws Exception {
        when(minioStorage.isEnabled()).thenReturn(true);
        when(minioStorage.putUserFile(anyString(), anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("users/1/abc/a.xlsx");
        MockMultipartFile xlsx = new MockMultipartFile(
                "file", "a.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                sampleXlsx()
        );
        mockMvc.perform(multipart("/QianXunService/data/files/upload").file(xlsx))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.files[0].kind").value("excel"));
        ArgumentCaptor<DataFile> captor = ArgumentCaptor.forClass(DataFile.class);
        verify(fileRepository, times(1)).insert(captor.capture());
        assertThat(captor.getValue().detailText()).contains("苹果");
        assertThat(captor.getValue().detailJson()).contains("品名");
    }

    @Test
    void uploadFile_shouldAcceptLegacyXls() throws Exception {
        when(minioStorage.isEnabled()).thenReturn(true);
        when(minioStorage.putUserFile(anyString(), anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("users/1/abc/a.xls");
        MockMultipartFile xls = new MockMultipartFile(
                "file", "a.xls", "application/vnd.ms-excel", sampleXls()
        );
        mockMvc.perform(multipart("/QianXunService/data/files/upload").file(xls))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.files[0].kind").value("excel"));
        ArgumentCaptor<DataFile> captor = ArgumentCaptor.forClass(DataFile.class);
        verify(fileRepository, times(1)).insert(captor.capture());
        assertThat(captor.getValue().detailText()).contains("销量");
    }

    private static byte[] sampleDocx(String text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText(text);
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] sampleXlsx() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("销售");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("品名");
            header.createCell(1).setCellValue("数量");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("苹果");
            row.createCell(1).setCellValue(12);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] sampleXls() throws Exception {
        try (HSSFWorkbook wb = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("报表");
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("销量");
            row.createCell(1).setCellValue(9);
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void publicDownload_shouldReturn404WhenMissing() throws Exception {
        when(fileRepository.findByPublicToken("nope")).thenReturn(Optional.empty());
        mockMvc.perform(get("/QianXunService/data/files/public/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicDownload_shouldStreamObject() throws Exception {
        Instant t = Instant.now();
        when(fileRepository.findByPublicToken("tok1")).thenReturn(Optional.of(
                new DataFile("f9", "1", "a.txt", "2026-08-12", "text", "n", null,
                        "users/1/f9/a.txt", "text/plain", 5L, "tok1", "", t, t)
        ));
        when(minioStorage.getObject("users/1/f9/a.txt")).thenReturn(new ByteArrayInputStream("hello".getBytes()));
        mockMvc.perform(get("/QianXunService/data/files/public/tok1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain"))
                .andExpect(content().string("hello"));
    }

    @Test
    void publicFileDetail_shouldReturnWithoutLogin() throws Exception {
        Instant t = Instant.now();
        when(fileRepository.findByPublicToken("tok1")).thenReturn(Optional.of(
                new DataFile("f9", "99", "a.xlsx", "2026-08-12", "excel", "body", "[[\"a\",\"b\"]]",
                        "users/99/f9/a.xlsx", "application/vnd.ms-excel", 5L, "tok1", "", t, t)
        ));
        mockMvc.perform(get("/QianXunService/data/files/public/tok1/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("f9"))
                .andExpect(jsonPath("$.data.publicToken").value("tok1"))
                .andExpect(jsonPath("$.data.publicUrl").value("/QianXunService/data/files/public/tok1"));
    }

    @Test
    void publicFileDetail_should404WhenMissing() throws Exception {
        when(fileRepository.findByPublicToken("nope")).thenReturn(Optional.empty());
        mockMvc.perform(get("/QianXunService/data/files/public/nope/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void fileDetail_should404WhenPublicTokenMissing() throws Exception {
        when(fileRepository.findByPublicToken("gone")).thenReturn(Optional.empty());
        String req = """
                {"jsonArg":{"publicToken":"gone"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/files/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void publicFileDetail_should404WhenFolder() throws Exception {
        Instant t = Instant.now();
        when(fileRepository.findByPublicToken("fld2")).thenReturn(Optional.of(
                new DataFile("fdir2", "1", "dir", "2026-08-12", DataFile.KIND_FOLDER, null, null,
                        null, null, null, "fld2", "", t, t)
        ));
        mockMvc.perform(get("/QianXunService/data/files/public/fld2/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void fileDetail_should404WhenPublicTokenIsFolder() throws Exception {
        Instant t = Instant.now();
        when(fileRepository.findByPublicToken("fld")).thenReturn(Optional.of(
                new DataFile("fdir", "1", "dir", "2026-08-12", DataFile.KIND_FOLDER, null, null,
                        null, null, null, "fld", "", t, t)
        ));
        String req = """
                {"jsonArg":{"publicToken":"fld"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/files/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
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

    @Test
    void createFolder_shouldRejectBlankName() throws Exception {
        String req = """
                {"jsonArg":{"name":"..","parentPath":""},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/folders/create")
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        verify(fileRepository, never()).insert(any(DataFile.class));
    }

    @Test
    void createFolder_shouldInsertAtRoot() throws Exception {
        when(fileRepository.findFolder("1", "", "情报")).thenReturn(Optional.empty());
        String req = """
                {"jsonArg":{"name":"情报","parentPath":""},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/folders/create")
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.kind").value("folder"))
                .andExpect(jsonPath("$.data.name").value("情报"));
        ArgumentCaptor<DataFile> captor = ArgumentCaptor.forClass(DataFile.class);
        verify(fileRepository).insert(captor.capture());
        assertThat(captor.getValue().isFolder()).isTrue();
        assertThat(captor.getValue().folderPath()).isEmpty();
    }

    @Test
    void createFolder_shouldRejectDuplicate() throws Exception {
        Instant t = Instant.now();
        when(fileRepository.findFolder("1", "", "情报")).thenReturn(Optional.of(
                DataFile.of("fd1", "情报", "2026-08-13", DataFile.KIND_FOLDER, null, null, t, t)
        ));
        String req = """
                {"jsonArg":{"name":"情报"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/folders/create")
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void createFolder_shouldRejectMissingParent() throws Exception {
        when(fileRepository.findFolder("1", "", "不存在")).thenReturn(Optional.empty());
        String req = """
                {"jsonArg":{"name":"子目录","parentPath":"不存在"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/folders/create")
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void deleteFile_shouldRemoveOwnedObject() throws Exception {
        Instant t = Instant.now();
        when(fileRepository.findById("f-del")).thenReturn(Optional.of(
                new DataFile("f-del", "1", "a.txt", "2026-08-12", "text", "n", null,
                        "users/1/f-del/a.txt", "text/plain", 5L, "tok", "", t, t)
        ));
        when(fileRepository.deleteById("f-del")).thenReturn(1);
        String req = """
                {"jsonArg":{"id":"f-del"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/files/delete")
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(1));
        verify(minioStorage).deleteObject("users/1/f-del/a.txt");
        verify(fileRepository).deleteById("f-del");
    }

    @Test
    void deleteFile_shouldHideOtherUsers() throws Exception {
        Instant t = Instant.now();
        when(fileRepository.findById("f-x")).thenReturn(Optional.of(
                new DataFile("f-x", "99", "a.txt", "2026-08-12", "text", "n", null,
                        "k", "text/plain", 5L, "tok", "", t, t)
        ));
        String req = """
                {"jsonArg":{"id":"f-x"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/files/delete")
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
        verify(fileRepository, never()).deleteById(anyString());
    }

    @Test
    void deleteFile_shouldDeleteFolderTree() throws Exception {
        Instant t = Instant.now();
        DataFile folder = new DataFile("fd", "1", "情报", "2026-08-12", DataFile.KIND_FOLDER,
                null, null, null, null, null, null, "", t, t);
        when(fileRepository.findById("fd")).thenReturn(Optional.of(folder));
        when(fileRepository.listByUserId("1")).thenReturn(List.of(folder));
        when(fileRepository.deleteByIds(any())).thenReturn(1);
        String req = """
                {"jsonArg":{"id":"fd"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/files/delete")
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(fileRepository).deleteByIds(any());
    }

    @Test
    void deleteFolder_shouldRejectNullBody() throws Exception {
        mockMvc.perform(post("/QianXunService/data/folders/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void deleteFolder_shouldRemoveTree() throws Exception {
        Instant t = Instant.now();
        DataFile folder = new DataFile("fd", "1", "情报", "2026-08-12", DataFile.KIND_FOLDER,
                null, null, null, null, null, null, "", t, t);
        DataFile child = new DataFile("fc", "1", "a.txt", "2026-08-12", "text", "n", null,
                "users/1/fc/a.txt", "text/plain", 5L, "tok", "情报", t, t);
        when(fileRepository.findFolder("1", "", "情报")).thenReturn(Optional.of(folder));
        when(fileRepository.listByUserId("1")).thenReturn(List.of(folder, child));
        when(fileRepository.deleteByIds(any())).thenReturn(2);
        String req = """
                {"jsonArg":{"path":"情报"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/folders/delete")
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(2));
        verify(minioStorage).deleteObject("users/1/fc/a.txt");
    }

    @Test
    void deleteFolder_shouldReturn404WhenMissing() throws Exception {
        when(fileRepository.findFolder("1", "", "nope")).thenReturn(Optional.empty());
        when(fileRepository.listByUserId("1")).thenReturn(List.of());
        String req = """
                {"jsonArg":{"path":"nope"},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/folders/delete")
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void deleteFolder_shouldRejectRoot() throws Exception {
        String req = """
                {"jsonArg":{"path":""},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/folders/delete")
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void uploadFile_shouldPutIntoFolder() throws Exception {
        when(minioStorage.isEnabled()).thenReturn(true);
        when(minioStorage.putUserFile(anyString(), anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("users/1/abc/a.txt");
        when(fileRepository.findFolder("1", "", "情报")).thenReturn(Optional.empty());
        MockMultipartFile txt = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(multipart("/QianXunService/data/files/upload").file(txt).param("folderPath", "情报"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.files[0].folderPath").value("情报"));
        ArgumentCaptor<DataFile> captor = ArgumentCaptor.forClass(DataFile.class);
        verify(fileRepository, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues().get(0).isFolder()).isTrue();
        assertThat(captor.getAllValues().get(1).folderPath()).isEqualTo("情报");
        assertThat(captor.getAllValues().get(1).detailText()).contains("hello");
    }

    @Test
    void uploadFile_shouldExtractImageMeta() throws Exception {
        when(minioStorage.isEnabled()).thenReturn(true);
        when(minioStorage.putUserFile(anyString(), anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("users/1/abc/a.png");
        MockMultipartFile png = new MockMultipartFile("file", "shot.png", "image/png", samplePng());
        mockMvc.perform(multipart("/QianXunService/data/files/upload").file(png))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.files[0].kind").value("image"))
                .andExpect(jsonPath("$.data.files[0].preview").value(org.hamcrest.Matchers.containsString("像素")));
        ArgumentCaptor<DataFile> captor = ArgumentCaptor.forClass(DataFile.class);
        verify(fileRepository).insert(captor.capture());
        assertThat(captor.getValue().detailText()).contains("12×8");
    }

    @Test
    void uploadBatch_shouldCollectSuccessAndErrors() throws Exception {
        when(minioStorage.isEnabled()).thenReturn(true);
        when(minioStorage.putUserFile(anyString(), anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("users/1/abc/a.txt");
        MockMultipartFile ok = new MockMultipartFile("files", "a.txt", "text/plain", "hi".getBytes());
        MockMultipartFile bad = new MockMultipartFile("files", "a.exe", "application/octet-stream", "x".getBytes());
        mockMvc.perform(multipart("/QianXunService/data/files/upload-batch").file(ok).file(bad))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ok").value(1))
                .andExpect(jsonPath("$.data.fail").value(1))
                .andExpect(jsonPath("$.data.files[0].name").value("a.txt"));
    }

    @Test
    void uploadBatch_shouldRejectEmpty() throws Exception {
        mockMvc.perform(multipart("/QianXunService/data/files/upload-batch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void uploadFile_shouldUnpackZipIntoFolder() throws Exception {
        when(minioStorage.isEnabled()).thenReturn(true);
        when(minioStorage.putUserFile(anyString(), anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("users/1/abc/hello.txt");
        when(fileRepository.findFolder(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(bos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("hello.txt"));
            zos.write("from-zip".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        MockMultipartFile zip = new MockMultipartFile(
                "file", "pack.zip", "application/zip", bos.toByteArray());
        mockMvc.perform(multipart("/QianXunService/data/files/upload").file(zip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ok").value(1))
                .andExpect(jsonPath("$.data.files[0].name").value("hello.txt"));
        ArgumentCaptor<DataFile> captor = ArgumentCaptor.forClass(DataFile.class);
        verify(fileRepository, atLeast(2)).insert(captor.capture());
        assertThat(captor.getAllValues().stream().anyMatch(DataFile::isFolder)).isTrue();
        assertThat(captor.getAllValues().stream().anyMatch(f -> "hello.txt".equals(f.name()))).isTrue();
    }

    @Test
    void uploadBatch_shouldRespectRelativePaths() throws Exception {
        when(minioStorage.isEnabled()).thenReturn(true);
        when(minioStorage.putUserFile(anyString(), anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("users/1/abc/a.txt");
        when(fileRepository.findFolder(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        MockMultipartFile ok = new MockMultipartFile("files", "a.txt", "text/plain", "hi".getBytes());
        mockMvc.perform(multipart("/QianXunService/data/files/upload-batch")
                        .file(ok)
                        .param("relativePaths", "demo/sub/a.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ok").value(1))
                .andExpect(jsonPath("$.data.files[0].folderPath").value("demo/sub"));
    }

    @Test
    void createFolder_shouldRejectNullBody() throws Exception {
        mockMvc.perform(post("/QianXunService/data/folders/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void download_shouldReturnAttachment() throws Exception {
        Instant t = Instant.now();
        when(fileRepository.findById("f9")).thenReturn(Optional.of(
                new DataFile("f9", "1", "a.txt", "2026-08-12", "text", "n", null,
                        "users/1/f9/a.txt", "text/plain", 5L, "tok1", "", t, t)
        ));
        when(minioStorage.getObject("users/1/f9/a.txt")).thenReturn(new ByteArrayInputStream("hello".getBytes()));
        mockMvc.perform(get("/QianXunService/data/files/download/f9"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().string("hello"));
    }

    @Test
    void download_shouldReturn404ForFolder() throws Exception {
        Instant t = Instant.now();
        when(fileRepository.findById("fd")).thenReturn(Optional.of(
                new DataFile("fd", "1", "情报", "2026-08-12", DataFile.KIND_FOLDER,
                        null, null, null, null, null, null, "", t, t)
        ));
        mockMvc.perform(get("/QianXunService/data/files/download/fd"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteFile_shouldRejectBlankId() throws Exception {
        String req = """
                {"jsonArg":{"id":"  "},"generalArgument":{"userId":"1","loginName":"u","ip":"","coralKey":""}}
                """;
        mockMvc.perform(post("/QianXunService/data/files/delete")
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    private static byte[] samplePng() throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(12, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(img, "png", out);
            return out.toByteArray();
        }
    }
}
