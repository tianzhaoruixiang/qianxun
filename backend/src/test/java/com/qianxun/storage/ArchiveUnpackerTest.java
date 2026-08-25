package com.qianxun.storage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchiveUnpackerTest {

    @Test
    void unpackZip_shouldExtractNestedLayout() throws Exception {
        byte[] zip = zipOf(
                entry("readme.txt", "hello"),
                entry("docs/a.txt", "doc-a"),
                entry("docs/b.txt", "doc-b")
        );
        List<ArchiveUnpacker.ExtractedEntry> entries = ArchiveUnpacker.unpackZip(zip, ArchiveUnpacker.Limits.defaults());
        assertThat(entries).hasSize(3);
        assertThat(entries).extracting(ArchiveUnpacker.ExtractedEntry::relativePath)
                .containsExactlyInAnyOrder("readme.txt", "docs/a.txt", "docs/b.txt");
        assertThat(new String(entries.stream().filter(e -> e.filename().equals("a.txt")).findFirst().orElseThrow().bytes(),
                StandardCharsets.UTF_8)).isEqualTo("doc-a");
    }

    @Test
    void unpackZip_shouldRecursivelyExpandNestedZip() throws Exception {
        byte[] inner = zipOf(entry("inner.txt", "nested-ok"));
        byte[] outer = zipOf(
                entry("plain.txt", "root"),
                entry("pack/nested.zip", inner)
        );
        List<ArchiveUnpacker.ExtractedEntry> entries = ArchiveUnpacker.unpackZip(outer, ArchiveUnpacker.Limits.defaults());
        assertThat(entries).extracting(ArchiveUnpacker.ExtractedEntry::relativePath)
                .containsExactlyInAnyOrder("plain.txt", "pack/nested/inner.txt");
    }

    @Test
    void sanitize_shouldRejectZipSlip() {
        assertThat(ArchiveUnpacker.sanitizeZipRelativePath("../etc/passwd")).isEmpty();
        assertThat(ArchiveUnpacker.sanitizeZipRelativePath("/abs/a.txt")).isEqualTo("abs/a.txt");
        assertThat(ArchiveUnpacker.sanitizeZipRelativePath("ok/../bad.txt")).isEmpty();
    }

    @Test
    void unpackZip_shouldEnforceEntryLimit() throws Exception {
        ZipBuilder b = new ZipBuilder();
        for (int i = 0; i < 5; i++) {
            b.add("f" + i + ".txt", "x");
        }
        byte[] zip = b.toBytes();
        assertThatThrownBy(() -> ArchiveUnpacker.unpackZip(zip, new ArchiveUnpacker.Limits(3, 8, 10_000_000)))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("文件数");
    }

    private static ZipEntrySpec entry(String name, String content) {
        return new ZipEntrySpec(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static ZipEntrySpec entry(String name, byte[] content) {
        return new ZipEntrySpec(name, content);
    }

    private static byte[] zipOf(ZipEntrySpec... entries) throws Exception {
        ZipBuilder b = new ZipBuilder();
        for (ZipEntrySpec e : entries) {
            b.add(e.name, e.bytes);
        }
        return b.toBytes();
    }

    private record ZipEntrySpec(String name, byte[] bytes) {}

    private static final class ZipBuilder {
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        private final ZipOutputStream zos = new ZipOutputStream(bos);

        void add(String name, byte[] bytes) throws Exception {
            zos.putNextEntry(new ZipEntry(name));
            zos.write(bytes);
            zos.closeEntry();
        }

        void add(String name, String text) throws Exception {
            add(name, text.getBytes(StandardCharsets.UTF_8));
        }

        byte[] toBytes() throws Exception {
            zos.close();
            return bos.toByteArray();
        }
    }
}
