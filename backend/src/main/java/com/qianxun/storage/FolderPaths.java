package com.qianxun.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 云盘虚拟文件夹路径：{@code ""} 为根，嵌套用 {@code /}，不含 {@code ..}。
 */
public final class FolderPaths {

    public static final int MAX_SEGMENT = 64;
    public static final int MAX_PATH = 512;

    private FolderPaths() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] parts = raw.replace('\\', '/').split("/");
        List<String> segs = new ArrayList<>();
        for (String p : parts) {
            String s = sanitizeSegment(p);
            if (s.isEmpty()) {
                continue;
            }
            segs.add(s);
        }
        if (segs.isEmpty()) {
            return "";
        }
        String out = String.join("/", segs);
        return out.length() > MAX_PATH ? out.substring(0, MAX_PATH) : out;
    }

    public static String sanitizeSegment(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty() || ".".equals(s) || "..".equals(s)) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '/' || ch == '\\' || ch < 32) {
                continue;
            }
            b.append(ch);
        }
        String v = b.toString().trim();
        if (v.isEmpty() || ".".equals(v) || "..".equals(v)) {
            return "";
        }
        return v.length() > MAX_SEGMENT ? v.substring(0, MAX_SEGMENT) : v;
    }

    public static String join(String parent, String name) {
        String p = normalize(parent);
        String n = sanitizeSegment(name);
        if (n.isEmpty()) {
            return p;
        }
        if (p.isEmpty()) {
            return n;
        }
        String out = p + "/" + n;
        return out.length() > MAX_PATH ? p : out;
    }

    /** {@code path} 等于 {@code ancestor}，或位于其下。 */
    public static boolean isSelfOrUnder(String path, String ancestor) {
        String p = normalize(path);
        String a = normalize(ancestor);
        if (a.isEmpty()) {
            return true;
        }
        return p.equals(a) || p.startsWith(a + "/");
    }

    public static String parentOf(String path) {
        String p = normalize(path);
        int slash = p.lastIndexOf('/');
        if (slash < 0) {
            return "";
        }
        return p.substring(0, slash);
    }

    public static String nameOf(String path) {
        String p = normalize(path);
        int slash = p.lastIndexOf('/');
        return slash < 0 ? p : p.substring(slash + 1);
    }

    public static boolean isFolderKind(String kind) {
        return kind != null && "folder".equals(kind.trim().toLowerCase(Locale.ROOT));
    }
}
