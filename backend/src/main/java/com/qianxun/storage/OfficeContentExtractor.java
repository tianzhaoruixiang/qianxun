package com.qianxun.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 从 Word（doc/docx）与 Excel（xls/xlsx）抽出正文，供中间数据预览和智能体理解。
 */
public final class OfficeContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(OfficeContentExtractor.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_TEXT = 200_000;
    private static final int MAX_EXCEL_ROWS = 300;
    private static final int MAX_EXCEL_COLS = 40;

    public record Result(String text, String excelJson) {
        public static Result empty() {
            return new Result("", null);
        }

        public boolean hasText() {
            return text != null && !text.isBlank();
        }
    }

    private OfficeContentExtractor() {}

    public static Result extract(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return Result.empty();
        }
        String ext = extension(filename);
        try {
            return switch (ext) {
                case "docx" -> fromDocx(bytes);
                case "doc" -> fromDoc(bytes);
                case "xlsx", "xls" -> fromExcel(bytes);
                case "csv" -> fromCsv(bytes);
                case "txt", "md" -> fromPlain(bytes);
                case "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "tif", "tiff"
                        -> ImageContentExtractor.extract(filename, bytes);
                default -> Result.empty();
            };
        } catch (Exception ex) {
            log.warn("解析文档失败 {}: {}", filename, ex.toString());
            return Result.empty();
        }
    }

    private static Result fromDocx(byte[] bytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return new Result(clip(extractor.getText()), null);
        }
    }

    private static Result fromDoc(byte[] bytes) throws Exception {
        try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(bytes));
             WordExtractor extractor = new WordExtractor(doc)) {
            return new Result(clip(extractor.getText()), null);
        }
    }

    private static Result fromExcel(byte[] bytes) throws Exception {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            DataFormatter fmt = new DataFormatter();
            StringBuilder text = new StringBuilder();
            List<List<String>> firstSheetRows = null;
            int sheetCount = wb.getNumberOfSheets();
            for (int s = 0; s < sheetCount; s++) {
                Sheet sheet = wb.getSheetAt(s);
                if (sheet == null) {
                    continue;
                }
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append("【工作表 ").append(sheet.getSheetName()).append("】\n");
                List<List<String>> rows = readSheet(sheet, fmt);
                if (firstSheetRows == null) {
                    firstSheetRows = rows;
                }
                for (List<String> row : rows) {
                    text.append(String.join("\t", row)).append('\n');
                    if (text.length() >= MAX_TEXT) {
                        break;
                    }
                }
                if (text.length() >= MAX_TEXT) {
                    break;
                }
            }
            String json = null;
            if (firstSheetRows != null && !firstSheetRows.isEmpty()) {
                json = JSON.writeValueAsString(firstSheetRows);
            }
            return new Result(clip(text.toString()), json);
        }
    }

    private static List<List<String>> readSheet(Sheet sheet, DataFormatter fmt) {
        List<List<String>> rows = new ArrayList<>();
        int last = Math.min(sheet.getLastRowNum(), MAX_EXCEL_ROWS - 1);
        for (int r = 0; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int lastCell = Math.min(row.getLastCellNum(), MAX_EXCEL_COLS);
            List<String> cells = new ArrayList<>(Math.max(lastCell, 0));
            boolean any = false;
            for (int c = 0; c < lastCell; c++) {
                Cell cell = row.getCell(c);
                String v = cell == null ? "" : fmt.formatCellValue(cell).trim();
                if (!v.isEmpty()) {
                    any = true;
                }
                cells.add(v);
            }
            if (any) {
                rows.add(cells);
            }
            if (rows.size() >= MAX_EXCEL_ROWS) {
                break;
            }
        }
        return rows;
    }

    private static Result fromCsv(byte[] bytes) {
        String raw = new String(bytes, StandardCharsets.UTF_8);
        if (raw.startsWith("\uFEFF")) {
            raw = raw.substring(1);
        }
        return new Result(clip(raw), null);
    }

    private static Result fromPlain(byte[] bytes) {
        return new Result(clip(new String(bytes, StandardCharsets.UTF_8)), null);
    }

    private static String clip(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (t.length() > MAX_TEXT) {
            return t.substring(0, MAX_TEXT) + "\n…（正文已截断）";
        }
        return t;
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
