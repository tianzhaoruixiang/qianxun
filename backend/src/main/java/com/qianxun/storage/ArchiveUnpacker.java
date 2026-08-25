package com.qianxun.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ZIP 递归解压：展开嵌套压缩包，并做 Zip Slip / 体积 / 条目数防护。
 */
public final class ArchiveUnpacker {

    public static final int DEFAULT_MAX_ENTRIES = 2000;
    public static final int DEFAULT_MAX_DEPTH = 8;
    public static final long DEFAULT_MAX_TOTAL_BYTES = 512L * 1024 * 1024;

    public record ExtractedEntry(String relativePath, String filename, byte[] bytes) {}

    public record Limits(int maxEntries, int maxDepth, long maxTotalBytes) {
        public static Limits defaults() {
            return new Limits(DEFAULT_MAX_ENTRIES, DEFAULT_MAX_DEPTH, DEFAULT_MAX_TOTAL_BYTES);
        }
    }

    private ArchiveUnpacker() {}

    public static boolean isZipName(String filename) {
        String n = filename == null ? "" : filename.trim().toLowerCase(Locale.ROOT);
        return n.endsWith(".zip");
    }

    public static List<ExtractedEntry> unpackZip(byte[] zipBytes, Limits limits) throws IOException {
        Limits lim = limits == null ? Limits.defaults() : limits;
        List<ExtractedEntry> out = new ArrayList<>();
        Counter counter = new Counter();
        unpackZipRecursive(zipBytes, "", 0, lim, out, counter);
        return out;
    }

    private static void unpackZipRecursive(
            byte[] zipBytes,
            String pathPrefix,
            int depth,
            Limits lim,
            List<ExtractedEntry> out,
            Counter counter
    ) throws IOException {
        if (depth > lim.maxDepth()) {
            throw new IOException("压缩包嵌套过深（超过 " + lim.maxDepth() + " 层）");
        }
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String rawName = entry.getName();
                if (rawName == null || rawName.isBlank()) {
                    zis.closeEntry();
                    continue;
                }
                String safeRel = sanitizeZipRelativePath(rawName);
                if (safeRel.isEmpty()) {
                    zis.closeEntry();
                    continue;
                }
                if (entry.isDirectory() || rawName.endsWith("/")) {
                    zis.closeEntry();
                    continue;
                }
                byte[] data = readEntryLimited(zis, lim.maxTotalBytes() - counter.totalBytes);
                counter.totalBytes += data.length;
                if (counter.totalBytes > lim.maxTotalBytes()) {
                    throw new IOException("解压后总大小超过限制");
                }
                String fullRel = pathPrefix.isEmpty() ? safeRel : pathPrefix + "/" + safeRel;
                String filename = FolderPaths.nameOf(fullRel.replace('\\', '/'));
                if (filename.isEmpty()) {
                    zis.closeEntry();
                    continue;
                }
                if (isZipName(filename)) {
                    String nestedPrefix = FolderPaths.parentOf(fullRel.replace('\\', '/'));
                    // 嵌套 zip：以去掉 .zip 的目录名为前缀展开
                    String zipStem = stripZipExtension(filename);
                    String nestBase = nestedPrefix.isEmpty() ? zipStem : nestedPrefix + "/" + zipStem;
                    unpackZipRecursive(data, nestBase, depth + 1, lim, out, counter);
                } else {
                    counter.entries++;
                    if (counter.entries > lim.maxEntries()) {
                        throw new IOException("压缩包内文件数超过 " + lim.maxEntries());
                    }
                    out.add(new ExtractedEntry(fullRel.replace('\\', '/'), filename, data));
                }
                zis.closeEntry();
            }
        }
    }

    /** 防 Zip Slip：拒绝绝对路径与 {@code ..}。 */
    public static String sanitizeZipRelativePath(String raw) {
        String name = raw.replace('\\', '/');
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (name.isBlank()) {
            return "";
        }
        String[] parts = name.split("/");
        List<String> segs = new ArrayList<>();
        for (String p : parts) {
            if (p.isBlank() || ".".equals(p)) {
                continue;
            }
            if ("..".equals(p)) {
                return "";
            }
            String s = FolderPaths.sanitizeSegment(p);
            if (s.isEmpty()) {
                return "";
            }
            segs.add(s);
        }
        if (segs.isEmpty()) {
            return "";
        }
        String out = String.join("/", segs);
        return out.length() > FolderPaths.MAX_PATH ? "" : out;
    }

    private static String stripZipExtension(String filename) {
        String n = filename.trim();
        if (n.toLowerCase(Locale.ROOT).endsWith(".zip") && n.length() > 4) {
            return FolderPaths.sanitizeSegment(n.substring(0, n.length() - 4));
        }
        return FolderPaths.sanitizeSegment(n);
    }

    private static byte[] readEntryLimited(InputStream in, long remainingBudget) throws IOException {
        if (remainingBudget <= 0) {
            throw new IOException("解压后总大小超过限制");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long read = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            read += n;
            if (read > remainingBudget) {
                throw new IOException("解压后总大小超过限制");
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static final class Counter {
        int entries;
        long totalBytes;
    }
}
