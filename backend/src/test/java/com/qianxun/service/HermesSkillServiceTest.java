package com.qianxun.service;

import com.qianxun.context.UserContext;
import com.qianxun.llm.HermesAgentClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HermesSkillServiceTest {

    @Mock
    private HermesAgentClient hermes;

    @InjectMocks
    private HermesSkillService service;

    @Test
    void sanitize_shouldRejectTraversal() {
        assertThat(HermesSkillService.sanitizeSkillRelative("../etc/passwd")).isEmpty();
        assertThat(HermesSkillService.sanitizeSkillRelative("refs/guide.md")).isEqualTo("refs/guide.md");
        assertThat(HermesSkillService.joinHermes("/home/p/skills/demo", "../x")).isEqualTo("/home/p/skills/demo");
        assertThat(HermesSkillService.joinHermes("/home/p/skills/demo", "refs/a.md"))
                .isEqualTo("/home/p/skills/demo/refs/a.md");
    }

    @Test
    void uploadZip_shouldRejectMissingSkillMd() throws Exception {
        when(hermes.isConfigured()).thenReturn(true);
        byte[] zip = zipOf("readme.txt", "hello");
        HermesSkillService.UploadResult r = service.uploadZip("worker", "pack.zip", zip);
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(s -> s.contains("SKILL.md"));
        verify(hermes, never()).createSkill(any(), any(), any(), any(), any());
    }

    @Test
    void uploadZip_shouldCreateSkillAndWriteSupportFiles() throws Exception {
        when(hermes.isConfigured()).thenReturn(true);
        when(hermes.getSkillContent(eq(UserContext.DEFAULT_USER_ID), eq("worker"), eq("demo"))).thenReturn(
                new HermesAgentClient.SkillContentResult(false, "demo", "", "", "技能不存在"));
        when(hermes.createSkill(eq(UserContext.DEFAULT_USER_ID), eq("worker"), eq("demo"), anyString(), any())).thenReturn(
                new HermesAgentClient.SkillWriteResult(true, "demo", "/home/p/skills/demo/SKILL.md", ""));
        when(hermes.writeManagedFile(eq(UserContext.DEFAULT_USER_ID), anyString(), any())).thenReturn(
                new HermesAgentClient.ManagedWriteResult(true, "", ""));

        byte[] zip = zipOfEntries(
                entry("demo/SKILL.md", "---\nname: demo\n---\n# Demo"),
                entry("demo/refs/note.md", "note"),
                entry("demo/../secret.txt", "nope")
        );
        HermesSkillService.UploadResult r = service.uploadZip("worker", "demo.zip", zip);
        assertThat(r.ok()).isTrue();
        assertThat(r.installed()).contains("demo");
        verify(hermes).createSkill(eq(UserContext.DEFAULT_USER_ID), eq("worker"), eq("demo"), anyString(), any());
        verify(hermes).writeManagedFile(eq(UserContext.DEFAULT_USER_ID), eq("/home/p/skills/demo/refs/note.md"), any());
        verify(hermes, never()).writeManagedFile(eq(UserContext.DEFAULT_USER_ID), eq("/home/p/skills/demo/secret.txt"), any());
    }

    @Test
    void isTextPath_shouldAllowSkillMd() {
        assertThat(HermesSkillService.isTextPath("SKILL.md")).isTrue();
        assertThat(HermesSkillService.isTextPath("refs/a.md")).isTrue();
        assertThat(HermesSkillService.isTextPath("bin/tool")).isFalse();
        assertThat(HermesSkillService.isSensitiveName(".env")).isTrue();
        assertThat(HermesSkillService.isSensitiveName("SKILL.md")).isFalse();
    }

    private static ZipEntrySpec entry(String name, String content) {
        return new ZipEntrySpec(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] zipOf(String name, String content) throws Exception {
        return zipOfEntries(entry(name, content));
    }

    private static byte[] zipOfEntries(ZipEntrySpec... entries) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (ZipEntrySpec e : entries) {
                zos.putNextEntry(new ZipEntry(e.name));
                zos.write(e.bytes);
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    private record ZipEntrySpec(String name, byte[] bytes) {}
}
