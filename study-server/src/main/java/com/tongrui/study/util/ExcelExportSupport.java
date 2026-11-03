package com.tongrui.study.util;

import com.tongrui.study.common.exception.BusinessException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.usermodel.FontScheme;
import org.apache.poi.xssf.usermodel.XSSFFont;
import java.math.BigDecimal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ExcelExportSupport {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String FONT_NAME = "Microsoft YaHei";

    private ExcelExportSupport() {
    }

    public static void applyDefaultFont(Workbook workbook) {
        Font font = workbook.getFontAt(0);
        font.setFontName(FONT_NAME);
        font.setFontHeightInPoints((short) 10);
        if (font instanceof XSSFFont xssfFont) {
            xssfFont.setScheme(FontScheme.NONE);
        }
    }

    public static Styles createStyles(Workbook workbook) {
        Font titleFont = workbook.createFont();
        titleFont.setFontName(FONT_NAME);
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        title.setAlignment(HorizontalAlignment.CENTER);
        title.setVerticalAlignment(VerticalAlignment.CENTER);

        Font headerFont = workbook.createFont();
        headerFont.setFontName(FONT_NAME);
        headerFont.setFontHeightInPoints((short) 11);
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        CellStyle header = borderedStyle(workbook, true);
        header.setFont(headerFont);
        header.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);

        Font bodyFont = workbook.createFont();
        bodyFont.setFontName(FONT_NAME);
        bodyFont.setFontHeightInPoints((short) 10);
        CellStyle text = borderedStyle(workbook, false);
        text.setFont(bodyFont);
        CellStyle wrap = borderedStyle(workbook, true);
        wrap.setFont(bodyFont);

        Font noteFont = workbook.createFont();
        noteFont.setFontName(FONT_NAME);
        noteFont.setFontHeightInPoints((short) 10);
        noteFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        CellStyle note = workbook.createCellStyle();
        note.setFont(noteFont);
        note.setWrapText(true);
        note.setVerticalAlignment(VerticalAlignment.TOP);

        Font promptFont = workbook.createFont();
        promptFont.setFontName(FONT_NAME);
        promptFont.setFontHeightInPoints((short) 11);
        promptFont.setBold(true);
        CellStyle prompt = borderedStyle(workbook, true);
        prompt.setFont(promptFont);
        prompt.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        prompt.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        prompt.setAlignment(HorizontalAlignment.LEFT);
        prompt.setVerticalAlignment(VerticalAlignment.CENTER);
        CellStyle percentage = workbook.createCellStyle();
        percentage.cloneStyleFrom(text);
        percentage.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
        percentage.setAlignment(HorizontalAlignment.RIGHT);
        return new Styles(title, header, text, wrap, note, prompt, percentage);
    }

    private static CellStyle borderedStyle(Workbook workbook, boolean wrap) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(wrap);
        return style;
    }

    public static Row titleRow(Sheet sheet, String title, int lastColumn, Styles styles) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(26);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(styles.title());
        if (lastColumn > 0) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, lastColumn));
        }
        return row;
    }

    public static void writeHeader(Row row, String[] values, Styles styles) {
        for (int index = 0; index < values.length; index++) {
            writeText(row, index, values[index], styles.header());
        }
        row.setHeightInPoints(24);
    }

    public static void writeText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(safeText(value));
        cell.setCellStyle(style);
    }

    public static void writeNumber(Row row, int column, Number value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? 0D : value.doubleValue());
        cell.setCellStyle(style);
    }

    public static void writePercentage(Row row, int column, BigDecimal value, Styles styles) {
        BigDecimal rate = value == null ? BigDecimal.ZERO : value;
        writeNumber(row, column, rate.movePointLeft(2), styles.percentage());
    }

    public static String safeText(String value) {
        if (value == null) {
            return "";
        }
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    public static String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMAT);
    }

    public static void setColumnWidths(Sheet sheet, int... widths) {
        for (int index = 0; index < widths.length; index++) {
            sheet.setColumnWidth(index, Math.min(widths[index], 80) * 256);
        }
    }

    public static byte[] toBytes(Workbook workbook) {
        try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException(500, "生成Excel文件失败");
        }
    }

    public record Styles(CellStyle title, CellStyle header, CellStyle text, CellStyle wrap,
                         CellStyle note, CellStyle prompt, CellStyle percentage) {
    }
}
