package com.tongrui.study.service;

import com.tongrui.study.dto.PersonalQuestionFilter;
import com.tongrui.study.util.ExcelExportSupport;
import com.tongrui.study.vo.ExcelExportFile;
import com.tongrui.study.vo.FavoriteVO;
import com.tongrui.study.vo.PracticeOptionVO;
import com.tongrui.study.vo.WrongQuestionVO;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PersonalQuestionExcelExportService {

    private static final int MIN_DISPLAY_LINES = 4;
    private static final int MAX_DISPLAY_LINES = 18;
    private static final float LINE_HEIGHT_POINTS = 15F;
    private static final float CELL_PADDING_POINTS = 6F;

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "SINGLE", "单选题", "MULTIPLE", "多选题", "JUDGE", "判断题");
    private static final Map<Integer, String> DIFFICULTY_LABELS = Map.of(
            1, "简单", 2, "中等", 3, "困难");

    private final WrongQuestionService wrongQuestionService;
    private final FavoriteService favoriteService;

    public PersonalQuestionExcelExportService(WrongQuestionService wrongQuestionService,
                                              FavoriteService favoriteService) {
        this.wrongQuestionService = wrongQuestionService;
        this.favoriteService = favoriteService;
    }

    public ExcelExportFile exportWrongQuestions(Long userId) {
        return exportWrongQuestions(userId, new PersonalQuestionFilter());
    }

    public ExcelExportFile exportWrongQuestions(Long userId, PersonalQuestionFilter filter) {
        List<WrongQuestionVO> records = wrongQuestionService.listForExport(userId, filter);
        Workbook workbook = new XSSFWorkbook();
        ExcelExportSupport.Styles styles = ExcelExportSupport.createStyles(workbook);
        Sheet sheet = workbook.createSheet("我的错题本");
        ExcelExportSupport.titleRow(sheet, "我的错题本", 12, styles);
        writeExportTime(sheet, 12, styles);

        Row header = sheet.createRow(2);
        ExcelExportSupport.writeHeader(header, new String[]{
                "序号", "科目", "章节", "知识点", "题型", "难度", "题目", "选项",
                "正确答案", "题目解析", "累计错误次数", "最近做错时间", "当前状态"
        }, styles);
        int rowIndex = 3;
        for (int index = 0; index < records.size(); index++) {
            WrongQuestionVO record = records.get(index);
            Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(dataRowHeight(record.getContent(), record.getOptions(), record.getAnalysis()));
            ExcelExportSupport.writeNumber(row, 0, index + 1, styles.text());
            ExcelExportSupport.writeText(row, 1, record.getSubjectName(), styles.wrap());
            ExcelExportSupport.writeText(row, 2, record.getChapterName(), styles.wrap());
            ExcelExportSupport.writeText(row, 3, record.getKnowledgePointName(), styles.wrap());
            ExcelExportSupport.writeText(row, 4, typeLabel(record.getType()), styles.text());
            ExcelExportSupport.writeText(row, 5, difficultyLabel(record.getDifficulty()), styles.text());
            ExcelExportSupport.writeText(row, 6, record.getContent(), styles.wrap());
            ExcelExportSupport.writeText(row, 7, optionText(record.getOptions()), styles.wrap());
            ExcelExportSupport.writeText(row, 8, answerText(record.getCorrectAnswer()), styles.wrap());
            ExcelExportSupport.writeText(row, 9, record.getAnalysis(), styles.wrap());
            ExcelExportSupport.writeNumber(row, 10, record.getWrongCount(), styles.text());
            ExcelExportSupport.writeText(row, 11,
                    ExcelExportSupport.formatDateTime(record.getLastWrongAt()), styles.text());
            ExcelExportSupport.writeText(row, 12, statusText(record.getQuestionStatus()), styles.text());
        }
        rowIndex = writeEmptyRow(sheet, rowIndex, records.isEmpty(), 12, styles);
        finishSheet(sheet, rowIndex, 12,
                8, 16, 20, 20, 11, 10, 44, 46, 16, 42, 14, 21, 12);
        return new ExcelExportFile("我的错题本_" + LocalDate.now().format(FILE_DATE) + ".xlsx",
                ExcelExportSupport.toBytes(workbook));
    }

    public ExcelExportFile exportFavorites(Long userId) {
        return exportFavorites(userId, new PersonalQuestionFilter());
    }

    public ExcelExportFile exportFavorites(Long userId, PersonalQuestionFilter filter) {
        List<FavoriteVO> records = favoriteService.listForExport(userId, filter);
        Workbook workbook = new XSSFWorkbook();
        ExcelExportSupport.Styles styles = ExcelExportSupport.createStyles(workbook);
        Sheet sheet = workbook.createSheet("我的收藏题目");
        ExcelExportSupport.titleRow(sheet, "我的收藏题目", 9, styles);
        writeExportTime(sheet, 9, styles);

        Row header = sheet.createRow(2);
        ExcelExportSupport.writeHeader(header, new String[]{
                "序号", "科目", "章节", "知识点", "题型", "难度", "题目", "选项", "收藏时间", "当前状态"
        }, styles);
        int rowIndex = 3;
        for (int index = 0; index < records.size(); index++) {
            FavoriteVO record = records.get(index);
            Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(dataRowHeight(record.getContent(), record.getOptions(), null));
            ExcelExportSupport.writeNumber(row, 0, index + 1, styles.text());
            ExcelExportSupport.writeText(row, 1, record.getSubjectName(), styles.wrap());
            ExcelExportSupport.writeText(row, 2, record.getChapterName(), styles.wrap());
            ExcelExportSupport.writeText(row, 3, record.getKnowledgePointName(), styles.wrap());
            ExcelExportSupport.writeText(row, 4, typeLabel(record.getType()), styles.text());
            ExcelExportSupport.writeText(row, 5, difficultyLabel(record.getDifficulty()), styles.text());
            ExcelExportSupport.writeText(row, 6, record.getContent(), styles.wrap());
            ExcelExportSupport.writeText(row, 7, optionText(record.getOptions()), styles.wrap());
            ExcelExportSupport.writeText(row, 8,
                    ExcelExportSupport.formatDateTime(record.getCreatedAt()), styles.text());
            ExcelExportSupport.writeText(row, 9, statusText(record.getQuestionStatus()), styles.text());
        }
        rowIndex = writeEmptyRow(sheet, rowIndex, records.isEmpty(), 9, styles);
        finishSheet(sheet, rowIndex, 9, 8, 16, 20, 20, 11, 10, 44, 46, 21, 12);
        return new ExcelExportFile("我的收藏题目_" + LocalDate.now().format(FILE_DATE) + ".xlsx",
                ExcelExportSupport.toBytes(workbook));
    }

    // Approximate wrapped lines at existing column widths; never depend on Excel auto-fit.
    private float dataRowHeight(String content, List<PracticeOptionVO> options, String analysis) {
        int lines = Math.max(MIN_DISPLAY_LINES, estimatedLines(optionText(options), 44));
        lines = Math.max(lines, estimatedLines(content, 42));
        lines = Math.max(lines, estimatedLines(analysis, 40));
        return Math.min(lines, MAX_DISPLAY_LINES) * LINE_HEIGHT_POINTS + CELL_PADDING_POINTS;
    }

    private int estimatedLines(String text, int columnUnits) {
        if (text == null || text.isEmpty()) return 1;
        int lines = 0;
        for (String explicitLine : text.split("\\r\\n|\\r|\\n", -1)) {
            // CJK and full-width mathematical symbols occupy approximately two Latin units.
            int units = explicitLine.codePoints().map(codePoint -> codePoint < 128 ? 1 : 2).sum();
            lines += Math.max(1, (units + columnUnits - 1) / columnUnits);
            if (lines >= MAX_DISPLAY_LINES) return MAX_DISPLAY_LINES;
        }
        return lines;
    }

    private void writeExportTime(Sheet sheet, int lastColumn, ExcelExportSupport.Styles styles) {
        Row row = sheet.createRow(1);
        ExcelExportSupport.writeText(row, 0, "导出时间", styles.note());
        ExcelExportSupport.writeText(row, 1,
                ExcelExportSupport.formatDateTime(LocalDateTime.now()), styles.note());
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 1, lastColumn));
    }

    private int writeEmptyRow(Sheet sheet, int rowIndex, boolean empty, int lastColumn,
                              ExcelExportSupport.Styles styles) {
        if (!empty) {
            return rowIndex;
        }
        Row row = sheet.createRow(rowIndex++);
        ExcelExportSupport.writeText(row, 0, "暂无数据", styles.wrap());
        sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, lastColumn));
        return rowIndex;
    }

    private void finishSheet(Sheet sheet, int rowIndex, int lastColumn, int... widths) {
        sheet.createFreezePane(0, 3);
        sheet.setAutoFilter(new CellRangeAddress(2, Math.max(2, rowIndex - 1), 0, lastColumn));
        ExcelExportSupport.setColumnWidths(sheet, widths);
    }

    private String optionText(List<PracticeOptionVO> options) {
        if (options == null || options.isEmpty()) {
            return "";
        }
        return options.stream()
                .map(option -> option.getOptionKey() + ". " + option.getContent())
                .collect(Collectors.joining("\n"));
    }

    private String typeLabel(String type) {
        return type == null ? "" : TYPE_LABELS.getOrDefault(type, type);
    }

    private String difficultyLabel(Integer difficulty) {
        return difficulty == null ? "" : DIFFICULTY_LABELS.getOrDefault(difficulty, "");
    }

    private String answerText(String answer) {
        if ("TRUE".equals(answer)) {
            return "正确";
        }
        if ("FALSE".equals(answer)) {
            return "错误";
        }
        return answer;
    }

    private String statusText(Integer status) {
        return Integer.valueOf(1).equals(status) ? "可练习" : "已禁用";
    }
}
