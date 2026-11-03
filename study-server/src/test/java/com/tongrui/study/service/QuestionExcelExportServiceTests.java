package com.tongrui.study.service;

import com.tongrui.study.dto.QuestionExportFilter;
import com.tongrui.study.entity.User;
import com.tongrui.study.mapper.UserMapper;
import com.tongrui.study.vo.ExcelExportFile;
import com.tongrui.study.vo.QuestionOptionVO;
import com.tongrui.study.vo.QuestionVO;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionExcelExportServiceTests {

    @Mock
    private QuestionService questionService;
    @Mock
    private UserMapper userMapper;

    @Test
    void exportsAllFilteredRowsSortedOptionsAndFormulaSafeText() throws Exception {
        QuestionExportFilter filter = new QuestionExportFilter(1L, 2L, 3L,
                "SINGLE", 2, 1, "集合");
        QuestionVO selection = QuestionVO.builder()
                .id(10L).subjectName("Java").chapterName("基础").knowledgePointName("集合")
                .type("SINGLE").difficulty(2).status(1).content("=2+3")
                .options(List.of(new QuestionOptionVO(2L, "B", "+危险", 2),
                        new QuestionOptionVO(1L, "A", "安全", 1)))
                .answer("A").analysis("@analysis").createdBy(5L)
                .createdAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 9, 2, 11, 0)).build();
        QuestionVO judge = QuestionVO.builder()
                .id(11L).subjectName("Java").chapterName("基础").knowledgePointName("判断")
                .type("JUDGE").difficulty(1).status(0).content("判断内容")
                .options(List.of()).answer("TRUE").analysis("").createdBy(5L).build();
        User creator = new User();
        creator.setId(5L);
        creator.setNickname("-creator");
        when(questionService.listForExport(filter)).thenReturn(List.of(selection, judge));
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(creator));

        ExcelExportFile file = new QuestionExcelExportService(questionService, userMapper).export(filter);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            Sheet sheet = workbook.getSheet("题库");
            DataFormatter formatter = new DataFormatter();
            assertEquals(6, sheet.getPhysicalNumberOfRows());
            assertTrue(formatter.formatCellValue(sheet.getRow(2).getCell(1)).contains("keyword=集合"));
            assertEquals("'=2+3", formatter.formatCellValue(sheet.getRow(4).getCell(7)));
            assertEquals("A. 安全\nB. +危险", formatter.formatCellValue(sheet.getRow(4).getCell(8)));
            assertEquals("'@analysis", formatter.formatCellValue(sheet.getRow(4).getCell(10)));
            assertEquals("'-creator", formatter.formatCellValue(sheet.getRow(4).getCell(11)));
            assertEquals("", formatter.formatCellValue(sheet.getRow(5).getCell(8)));
            assertEquals(CellType.STRING, sheet.getRow(4).getCell(7).getCellType());
        }
        verify(questionService).listForExport(filter);
        verify(userMapper).selectBatchIds(anyCollection());
    }

    @Test
    void exportsReadableEmptyWorkbook() throws Exception {
        QuestionExportFilter filter = new QuestionExportFilter(null, null, null, null, null, null, null);
        when(questionService.listForExport(filter)).thenReturn(List.of());

        ExcelExportFile file = new QuestionExcelExportService(questionService, userMapper).export(filter);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            assertEquals("当前筛选条件下暂无题目",
                    workbook.getSheet("题库").getRow(4).getCell(0).getStringCellValue());
        }
    }
}
