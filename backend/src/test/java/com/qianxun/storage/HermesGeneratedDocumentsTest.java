package com.qianxun.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HermesGeneratedDocumentsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void pathsFromWriteFileArgs() {
        List<String> paths = HermesGeneratedDocuments.pathsFromTool(
                mapper, "write_file", "{\"path\":\"reports/销量.xlsx\"}", "wrote 1200 bytes");
        assertThat(paths).containsExactly("reports/销量.xlsx");
    }

    @Test
    void skipsSoulAndNonDocs() {
        List<String> paths = HermesGeneratedDocuments.pathsFromTool(
                mapper, "write_file", "{\"path\":\"SOUL.md\"}", "{\"file\":\"notes.txt\"}");
        assertThat(paths).isEmpty();
    }

    @Test
    void extractsFromResultText() {
        List<String> paths = HermesGeneratedDocuments.pathsFromTool(
                mapper, "patch", "{}", "Updated /tmp/brief.docx and ignore.bin");
        assertThat(paths).contains("/tmp/brief.docx").doesNotHaveDuplicates();
    }

    @Test
    void pathsFromBashPythonWritingXlsx() {
        List<String> paths = HermesGeneratedDocuments.pathsFromTool(
                mapper, "Bash",
                "{\"command\":\"python3 -c \\\"import openpyxl; wb.save('差旅费报销单_赵天祥.xlsx')\\\"\"}",
                "");
        assertThat(paths).contains("差旅费报销单_赵天祥.xlsx");
    }

    @Test
    void downloadCandidatesIncludeUserWorkspace() {
        assertThat(HermesGeneratedDocuments.downloadCandidates("差旅费报销单_赵天祥.xlsx", "1"))
                .contains(
                        "差旅费报销单_赵天祥.xlsx",
                        "/opt/data/1/workspace/差旅费报销单_赵天祥.xlsx",
                        "workspace/差旅费报销单_赵天祥.xlsx");
        assertThat(HermesGeneratedDocuments.downloadCandidates("差旅费报销单_赵天祥.xlsx", "1", "sess-a"))
                .contains(
                        "/opt/data/1/workspace/qx/sess-a/差旅费报销单_赵天祥.xlsx",
                        "workspace/qx/sess-a/差旅费报销单_赵天祥.xlsx");
    }

    @Test
    void ignoresNonWriteTools() {
        List<String> paths = HermesGeneratedDocuments.pathsFromTool(
                mapper, "web_search", "{\"path\":\"a.xlsx\"}", "");
        assertThat(paths).isEmpty();
    }

    @Test
    void chatMarkdownUsesRelativeLink() {
        assertThat(HermesGeneratedDocuments.chatMarkdown("a.xlsx", "/QianXunService/data/files/public/tok"))
                .contains("[a.xlsx](/QianXunService/data/files/public/tok)");
    }

    @Test
    void pathsFromAssistantPublicUrlAndChineseName() {
        List<String> paths = HermesGeneratedDocuments.pathsFromAssistantText(
                "下载：`/QianXunService/data/files/public/sample_document.md` 以及 销量.xlsx");
        assertThat(paths).contains("sample_document.md", "销量.xlsx");
    }

    @Test
    void downloadCandidatesIncludeHermesPublicDir() {
        assertThat(HermesGeneratedDocuments.downloadCandidates("sample_document.md"))
                .contains("sample_document.md", "files/public/sample_document.md", "/opt/data/files/public/sample_document.md");
        assertThat(HermesGeneratedDocuments.downloadCandidates("/QianXunService/data/files/public/a.docx"))
                .contains("files/public/a.docx", "a.docx");
    }

    @Test
    void chatMarkdownKeepsChineseFilename() {
        String md = HermesGeneratedDocuments.chatMarkdown(
                "差旅费报销单_张伟.xlsx",
                "/QianXunService/data/files/public/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(md).contains("[差旅费报销单_张伟.xlsx](/QianXunService/data/files/public/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa)");
    }
}
