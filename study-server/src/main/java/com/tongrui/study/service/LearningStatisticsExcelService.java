package com.tongrui.study.service;

import com.tongrui.study.entity.User;
import com.tongrui.study.mapper.UserMapper;
import com.tongrui.study.util.ExcelExportSupport;
import com.tongrui.study.vo.DailyStatisticsVO;
import com.tongrui.study.vo.ExcelExportFile;
import com.tongrui.study.vo.KnowledgePointStatisticsVO;
import com.tongrui.study.vo.LearningStatisticsVO;
import com.tongrui.study.vo.StatisticsSummaryVO;
import com.tongrui.study.vo.SubjectStatisticsVO;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class LearningStatisticsExcelService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private final LearningStatisticsService learningStatisticsService;
    private final UserMapper userMapper;

    public LearningStatisticsExcelService(LearningStatisticsService learningStatisticsService,
                                          UserMapper userMapper) {
        this.learningStatisticsService = learningStatisticsService;
        this.userMapper = userMapper;
    }

    public ExcelExportFile export(Long userId, int days) {
        LearningStatisticsVO statistics = learningStatisticsService.overview(userId, days);
        User user = userMapper.selectById(userId);
        String displayName = user == null ? "用户" + userId
                : (StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        LocalDateTime exportedAt = LocalDateTime.now();

        Workbook workbook = new XSSFWorkbook();
        ExcelExportSupport.Styles styles = ExcelExportSupport.createStyles(workbook);
        writeOverview(workbook, styles, statistics.getSummary(), userId, displayName, days, exportedAt);
        writeSubjects(workbook, styles, statistics.getSubjectStatistics());
        writeKnowledgePoints(workbook, styles, statistics.getKnowledgePointStatistics(), "知识点统计",
                "全部历史答题统计");
        writeKnowledgePoints(workbook, styles, statistics.getWeakKnowledgePoints(), "薄弱知识点",
                "全部历史统计；至少作答2次后按规则筛选");
        writeTrend(workbook, styles, statistics.getDailyTrend(), days);

        String filename = "学习统计报告_" + exportedAt.format(FILE_TIME) + ".xlsx";
        return new ExcelExportFile(filename, ExcelExportSupport.toBytes(workbook));
    }

    private void writeOverview(Workbook workbook, ExcelExportSupport.Styles styles,
                               StatisticsSummaryVO summary, Long userId, String displayName,
                               int days, LocalDateTime exportedAt) {
        Sheet sheet = workbook.createSheet("学习概览");
        ExcelExportSupport.titleRow(sheet, "学习统计报告", 3, styles);
        Row scope = sheet.createRow(1);
        writeOverviewItem(scope, "统计口径",
                "总体、科目、知识点和薄弱知识点均为全部历史答题统计；每日趋势仅覆盖最近" + days + "天", styles);
        scope.setHeightInPoints(36);

        writeOverviewItem(sheet.createRow(2), "当前用户", displayName, styles);
        writeOverviewItem(sheet.createRow(3), "用户ID", String.valueOf(userId), styles);
        writeOverviewItem(sheet.createRow(4), "导出时间", ExcelExportSupport.formatDateTime(exportedAt), styles);
        writeOverviewItem(sheet.createRow(5), "趋势范围", "最近" + days + "天", styles);

        Row header = sheet.createRow(7);
        ExcelExportSupport.writeHeader(header, new String[]{"指标", "数值"}, styles);
        int rowIndex = 8;
        rowIndex = writeMetric(sheet, rowIndex, "总答题数", summary.getTotalAnswerCount(), styles);
        rowIndex = writeMetric(sheet, rowIndex, "正确数", summary.getCorrectAnswerCount(), styles);
        rowIndex = writeMetric(sheet, rowIndex, "错误数", summary.getIncorrectAnswerCount(), styles);
        Row accuracyRow = sheet.createRow(rowIndex++);
        ExcelExportSupport.writeText(accuracyRow, 0, "正确率", styles.text());
        ExcelExportSupport.writePercentage(accuracyRow, 1, summary.getAccuracyRate(), styles);
        rowIndex = writeMetric(sheet, rowIndex, "已练习题目数", summary.getAnsweredQuestionCount(), styles);
        rowIndex = writeMetric(sheet, rowIndex, "学习天数", summary.getStudyDays(), styles);
        rowIndex = writeMetric(sheet, rowIndex, "当前错题数", summary.getWrongQuestionCount(), styles);
        writeMetric(sheet, rowIndex, "当前收藏数", summary.getFavoriteCount(), styles);
        sheet.createFreezePane(0, 8);
        sheet.setAutoFilter(new CellRangeAddress(7, rowIndex, 0, 1));
        ExcelExportSupport.setColumnWidths(sheet, 22, 48, 16, 16);
    }

    private void writeOverviewItem(Row row, String label, String value, ExcelExportSupport.Styles styles) {
        ExcelExportSupport.writeText(row, 0, label, styles.text());
        // Style every cell before merging so the entire perimeter retains thin borders.
        for (int column = 1; column <= 3; column++) {
            ExcelExportSupport.writeText(row, column, column == 1 ? value : "", styles.wrap());
        }
        row.getSheet().addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 1, 3));
    }

    private int writeMetric(Sheet sheet, int rowIndex, String label, Object value,
                            ExcelExportSupport.Styles styles) {
        Row row = sheet.createRow(rowIndex);
        ExcelExportSupport.writeText(row, 0, label, styles.text());
        if (value instanceof Number number) {
            ExcelExportSupport.writeNumber(row, 1, number, styles.text());
        } else {
            ExcelExportSupport.writeText(row, 1, String.valueOf(value), styles.text());
        }
        return rowIndex + 1;
    }

    private void writeSubjects(Workbook workbook, ExcelExportSupport.Styles styles,
                               List<SubjectStatisticsVO> items) {
        Sheet sheet = workbook.createSheet("科目统计");
        ExcelExportSupport.titleRow(sheet, "科目统计", 4, styles);
        writeScope(sheet, "统计口径：全部历史答题统计", 4, styles);
        Row header = sheet.createRow(3);
        ExcelExportSupport.writeHeader(header,
                new String[]{"科目", "答题数", "正确数", "错误数", "正确率"}, styles);
        int rowIndex = 4;
        for (SubjectStatisticsVO item : items) {
            Row row = sheet.createRow(rowIndex++);
            ExcelExportSupport.writeText(row, 0, item.getSubjectName(), styles.wrap());
            ExcelExportSupport.writeNumber(row, 1, item.getAnswerCount(), styles.text());
            ExcelExportSupport.writeNumber(row, 2, item.getCorrectCount(), styles.text());
            ExcelExportSupport.writeNumber(row, 3, item.getIncorrectCount(), styles.text());
            ExcelExportSupport.writePercentage(row, 4, item.getAccuracyRate(), styles);
        }
        rowIndex = writeEmptyRowIfNeeded(sheet, rowIndex, items.isEmpty(), 4, styles);
        finishTable(sheet, rowIndex, 4, 22, 12, 12, 12, 14);
    }

    private void writeKnowledgePoints(Workbook workbook, ExcelExportSupport.Styles styles,
                                      List<KnowledgePointStatisticsVO> items, String sheetName,
                                      String scopeText) {
        Sheet sheet = workbook.createSheet(sheetName);
        ExcelExportSupport.titleRow(sheet, sheetName, 6, styles);
        writeScope(sheet, "统计口径：" + scopeText, 6, styles);
        Row header = sheet.createRow(3);
        ExcelExportSupport.writeHeader(header,
                new String[]{"科目", "章节", "知识点", "答题数", "正确数", "错误数", "正确率"}, styles);
        int rowIndex = 4;
        for (KnowledgePointStatisticsVO item : items) {
            Row row = sheet.createRow(rowIndex++);
            ExcelExportSupport.writeText(row, 0, item.getSubjectName(), styles.wrap());
            ExcelExportSupport.writeText(row, 1, item.getChapterName(), styles.wrap());
            ExcelExportSupport.writeText(row, 2, item.getKnowledgePointName(), styles.wrap());
            ExcelExportSupport.writeNumber(row, 3, item.getAnswerCount(), styles.text());
            ExcelExportSupport.writeNumber(row, 4, item.getCorrectCount(), styles.text());
            ExcelExportSupport.writeNumber(row, 5, item.getIncorrectCount(), styles.text());
            ExcelExportSupport.writePercentage(row, 6, item.getAccuracyRate(), styles);
        }
        rowIndex = writeEmptyRowIfNeeded(sheet, rowIndex, items.isEmpty(), 6, styles);
        finishTable(sheet, rowIndex, 6, 16, 24, 22, 11, 11, 11, 14);
    }

    private void writeTrend(Workbook workbook, ExcelExportSupport.Styles styles,
                            List<DailyStatisticsVO> items, int days) {
        Sheet sheet = workbook.createSheet("最近趋势");
        ExcelExportSupport.titleRow(sheet, "最近" + days + "天答题趋势", 4, styles);
        writeScope(sheet, "统计口径：仅本工作表受最近" + days + "天范围限制", 4, styles);
        Row header = sheet.createRow(3);
        ExcelExportSupport.writeHeader(header,
                new String[]{"日期", "答题数", "正确数", "错误数", "正确率"}, styles);
        int rowIndex = 4;
        for (DailyStatisticsVO item : items) {
            Row row = sheet.createRow(rowIndex++);
            ExcelExportSupport.writeText(row, 0, item.getDate() == null ? "" : item.getDate().toString(), styles.text());
            ExcelExportSupport.writeNumber(row, 1, item.getAnswerCount(), styles.text());
            ExcelExportSupport.writeNumber(row, 2, item.getCorrectCount(), styles.text());
            ExcelExportSupport.writeNumber(row, 3, item.getIncorrectCount(), styles.text());
            ExcelExportSupport.writePercentage(row, 4, item.getAccuracyRate(), styles);
        }
        rowIndex = writeEmptyRowIfNeeded(sheet, rowIndex, items.isEmpty(), 4, styles);
        finishTable(sheet, rowIndex, 4, 16, 12, 12, 12, 14);
    }

    private void writeScope(Sheet sheet, String text, int lastColumn, ExcelExportSupport.Styles styles) {
        Row row = sheet.createRow(1);
        ExcelExportSupport.writeText(row, 0, text, styles.note());
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, lastColumn));
    }

    private int writeEmptyRowIfNeeded(Sheet sheet, int rowIndex, boolean empty, int lastColumn,
                                      ExcelExportSupport.Styles styles) {
        if (!empty) {
            return rowIndex;
        }
        Row row = sheet.createRow(rowIndex++);
        ExcelExportSupport.writeText(row, 0, "暂无数据", styles.wrap());
        sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, lastColumn));
        return rowIndex;
    }

    private void finishTable(Sheet sheet, int rowIndex, int lastColumn, int... widths) {
        sheet.createFreezePane(0, 4);
        sheet.setAutoFilter(new CellRangeAddress(3, Math.max(3, rowIndex - 1), 0, lastColumn));
        ExcelExportSupport.setColumnWidths(sheet, widths);
    }

}
