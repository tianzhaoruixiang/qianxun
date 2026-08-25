package com.qianxun.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 抽出图片元数据与 SVG 内嵌文字，供中间数据预览和智能体读图。
 */
public final class ImageContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(ImageContentExtractor.class);
    private static final Pattern SVG_TEXT = Pattern.compile(
            "<text[^>]*>([^<]*)</text>|<title[^>]*>([^<]*)</title>|<desc[^>]*>([^<]*)</desc>",
            Pattern.CASE_INSENSITIVE
    );
    private static final int MAX_SVG_TEXT = 8_000;

    private ImageContentExtractor() {}

    public static OfficeContentExtractor.Result extract(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return OfficeContentExtractor.Result.empty();
        }
        String ext = extension(filename);
        try {
            if ("svg".equals(ext)) {
                return fromSvg(filename, bytes);
            }
            return fromRaster(filename, ext, bytes);
        } catch (Exception ex) {
            log.debug("解析图片失败 {}: {}", filename, ex.toString());
            return generic(filename, bytes.length, ext);
        }
    }

    public static boolean isImageFilename(String filename) {
        return switch (extension(filename)) {
            case "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "tif", "tiff" -> true;
            default -> false;
        };
    }

    private static OfficeContentExtractor.Result fromRaster(String filename, String ext, byte[] bytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                return generic(filename, bytes.length, ext);
            }
            String text = "图片「" + filename + "」："
                    + ext.toUpperCase(Locale.ROOT)
                    + "，" + img.getWidth() + "×" + img.getHeight()
                    + " 像素，" + bytes.length + " 字节。"
                    + "请通过公开链接阅读画面中的文字、人物、物体与场景。";
            return new OfficeContentExtractor.Result(text, null);
        } catch (Exception ex) {
            return generic(filename, bytes.length, ext);
        }
    }

    private static OfficeContentExtractor.Result fromSvg(String filename, byte[] bytes) {
        String raw = new String(bytes, StandardCharsets.UTF_8);
        StringBuilder texts = new StringBuilder();
        Matcher m = SVG_TEXT.matcher(raw);
        while (m.find()) {
            String t = firstNonBlank(m.group(1), m.group(2), m.group(3));
            if (t == null || t.isBlank()) {
                continue;
            }
            if (texts.length() > 0) {
                texts.append('\n');
            }
            texts.append(t.strip());
            if (texts.length() >= MAX_SVG_TEXT) {
                break;
            }
        }
        StringBuilder out = new StringBuilder();
        out.append("SVG 图片「").append(filename).append("」，").append(bytes.length).append(" 字节。");
        if (texts.length() > 0) {
            out.append("\n内嵌文字：\n").append(texts);
        } else {
            out.append("请通过公开链接阅读矢量图内容。");
        }
        return new OfficeContentExtractor.Result(out.toString(), null);
    }

    private static OfficeContentExtractor.Result generic(String filename, int size, String ext) {
        String label = ext == null || ext.isBlank() ? "图片" : ext.toUpperCase(Locale.ROOT);
        return new OfficeContentExtractor.Result(
                "图片「" + filename + "」（" + label + "，" + size + " 字节）。请通过公开链接阅读画面内容。",
                null
        );
    }

    private static String firstNonBlank(String... vs) {
        if (vs == null) {
            return null;
        }
        for (String v : vs) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String extension(String filename) {
        String name = filename == null ? "" : filename.trim().toLowerCase(Locale.ROOT);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }
}
