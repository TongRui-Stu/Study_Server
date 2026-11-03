package com.tongrui.study.service;

import com.tongrui.study.entity.User;
import com.tongrui.study.mapper.UserMapper;
import com.tongrui.study.vo.DailyStatisticsVO;
import com.tongrui.study.vo.ExcelExportFile;
import com.tongrui.study.vo.KnowledgePointStatisticsVO;
import com.tongrui.study.vo.LearningStatisticsVO;
import com.tongrui.study.vo.StatisticsSummaryVO;
import com.tongrui.study.vo.SubjectStatisticsVO;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningStatisticsExcelServiceTests {

    @Mock
    private LearningStatisticsService statisticsService;
    @Mock
    private UserMapper userMapper;

    @Test
    void exportsCurrentUsersCompleteStatisticsWithClearScopes() throws Exception {
        StatisticsSummaryVO summary = summary(8L, 5L, 3L, new BigDecimal("62.50"));
        SubjectStatisticsVO subject = new SubjectStatisticsVO();
        subject.setSubjectName("Java");
        subject.setAnswerCount(8L);
        subject.setCorrectCount(5L);
        subject.setIncorrectCount(3L);
        subject.setAccuracyRate(new BigDecimal("62.50"));
        KnowledgePointStatisticsVO point = new KnowledgePointStatisticsVO();
        point.setSubjectName("Java");
        point.setChapterName("基础");
        point.setKnowledgePointName("集合");
        point.setAnswerCount(3L);
        point.setCorrectCount(1L);
        point.setIncorrectCount(2L);
        point.setAccuracyRate(new BigDecimal("33.33"));
        LearningStatisticsVO data = new LearningStatisticsVO(summary, List.of(subject), List.of(point),
                List.of(point), List.of(new DailyStatisticsVO(LocalDate.of(2026, 9, 26),
                2L, 1L, 1L, new BigDecimal("50.00"))));
        User user = new User();
        user.setId(7L);
        user.setUsername("student7");
        user.setNickname("=unsafe");
        when(statisticsService.overview(7L, 30)).thenReturn(data);
        when(userMapper.selectById(7L)).thenReturn(user);

        ExcelExportFile file = new LearningStatisticsExcelService(statisticsService, userMapper).export(7L, 30);

        assertTrue(file.filename().startsWith("学习统计报告_"));
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            assertEquals(List.of("学习概览", "科目统计", "知识点统计", "薄弱知识点", "最近趋势"),
                    sheetNames(workbook));
            DataFormatter formatter = new DataFormatter();
            Sheet overview = workbook.getSheet("学习概览");
            assertEquals("'=unsafe", formatter.formatCellValue(overview.getRow(2).getCell(1)));
            assertTrue(formatter.formatCellValue(overview.getRow(1).getCell(1)).contains("全部历史"));
            assertEquals("8", formatter.formatCellValue(overview.getRow(8).getCell(1)));
            assertEquals("62.50%", formatter.formatCellValue(overview.getRow(11).getCell(1)));
            assertPercentage(overview.getRow(11).getCell(1), 0.625);
            assertPercentage(workbook.getSheet("科目统计").getRow(4).getCell(4), 0.625);
            assertPercentage(workbook.getSheet("知识点统计").getRow(4).getCell(6), 0.3333);
            assertPercentage(workbook.getSheet("薄弱知识点").getRow(4).getCell(6), 0.3333);
            assertPercentage(workbook.getSheet("最近趋势").getRow(4).getCell(4), 0.5);
            for (int rowIndex = 1; rowIndex <= 5; rowIndex++) {
                for (int column = 0; column <= 3; column++) {
                    var cell = overview.getRow(rowIndex).getCell(column);
                    assertEquals(org.apache.poi.ss.usermodel.BorderStyle.THIN, cell.getCellStyle().getBorderTop());
                    assertEquals(org.apache.poi.ss.usermodel.BorderStyle.THIN, cell.getCellStyle().getBorderBottom());
                }
                assertEquals(org.apache.poi.ss.usermodel.BorderStyle.THIN,
                        overview.getRow(rowIndex).getCell(0).getCellStyle().getBorderLeft());
                assertEquals(org.apache.poi.ss.usermodel.BorderStyle.THIN,
                        overview.getRow(rowIndex).getCell(3).getCellStyle().getBorderRight());
                assertTrue(overview.getMergedRegions().contains(
                        new org.apache.poi.ss.util.CellRangeAddress(rowIndex, rowIndex, 1, 3)));
            }
            assertTrue(overview.getRow(1).getCell(1).getCellStyle().getWrapText());
            assertTrue(overview.getPaneInformation().isFreezePane());
            assertTrue(((org.apache.poi.xssf.usermodel.XSSFSheet) overview).getCTWorksheet().isSetAutoFilter());
            assertTrue(formatter.formatCellValue(workbook.getSheet("最近趋势").getRow(1).getCell(0))
                    .contains("最近30天"));
        }
        verify(statisticsService).overview(7L, 30);
        verify(userMapper).selectById(7L);
    }

    @Test
    void exportsValidWorkbookWhenUserHasNoLearningData() throws Exception {
        LearningStatisticsVO empty = new LearningStatisticsVO(summary(0L, 0L, 0L, BigDecimal.ZERO),
                List.of(), List.of(), List.of(), List.of());
        when(statisticsService.overview(9L, 7)).thenReturn(empty);

        ExcelExportFile file = new LearningStatisticsExcelService(statisticsService, userMapper).export(9L, 7);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            assertPercentage(workbook.getSheet("学习概览").getRow(11).getCell(1), 0);
            assertEquals("暂无数据", workbook.getSheet("科目统计").getRow(4).getCell(0).getStringCellValue());
            assertEquals("暂无数据", workbook.getSheet("最近趋势").getRow(4).getCell(0).getStringCellValue());
        }
    }

    private void assertPercentage(org.apache.poi.ss.usermodel.Cell cell, double expected) {
        assertEquals(org.apache.poi.ss.usermodel.CellType.NUMERIC, cell.getCellType());
        assertEquals(expected, cell.getNumericCellValue(), 0.0000001);
        assertEquals("0.00%", cell.getCellStyle().getDataFormatString());
        assertEquals(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT, cell.getCellStyle().getAlignment());
        assertEquals("Microsoft YaHei", cell.getSheet().getWorkbook()
                .getFontAt(cell.getCellStyle().getFontIndex()).getFontName());
        assertEquals(org.apache.poi.ss.usermodel.BorderStyle.THIN, cell.getCellStyle().getBorderBottom());
    }

    @Test
    void nullAccuracyRatesAreSafeNumericZeroPercentages() throws Exception {
        LearningStatisticsVO empty = new LearningStatisticsVO(summary(0, 0, 0, null),
                List.of(), List.of(), List.of(), List.of());
        when(statisticsService.overview(12L, 7)).thenReturn(empty);
        var file = new LearningStatisticsExcelService(statisticsService, userMapper).export(12L, 7);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            assertPercentage(workbook.getSheetAt(0).getRow(11).getCell(1), 0);
        }
    }

    private StatisticsSummaryVO summary(long total, long correct, long incorrect, BigDecimal rate) {
        StatisticsSummaryVO summary = new StatisticsSummaryVO();
        summary.setTotalAnswerCount(total);
        summary.setCorrectAnswerCount(correct);
        summary.setIncorrectAnswerCount(incorrect);
        summary.setAccuracyRate(rate);
        summary.setAnsweredQuestionCount(total);
        summary.setWrongQuestionCount(incorrect);
        summary.setFavoriteCount(0L);
        summary.setStudyDays(total == 0 ? 0L : 1L);
        return summary;
    }

    private List<String> sheetNames(Workbook workbook) {
        return java.util.stream.IntStream.range(0, workbook.getNumberOfSheets())
                .mapToObj(workbook::getSheetName).toList();
    }
}
