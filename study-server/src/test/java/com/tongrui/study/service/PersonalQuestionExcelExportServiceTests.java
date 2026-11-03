package com.tongrui.study.service;

import com.tongrui.study.vo.ExcelExportFile;
import com.tongrui.study.vo.FavoriteVO;
import com.tongrui.study.vo.PracticeOptionVO;
import com.tongrui.study.vo.WrongQuestionVO;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalQuestionExcelExportServiceTests {

    @Mock
    private WrongQuestionService wrongQuestionService;
    @Mock
    private FavoriteService favoriteService;

    @Test
    void wrongQuestionExportContainsAnswersAnalysisAndFormulaSafeText() throws Exception {
        WrongQuestionVO record = new WrongQuestionVO();
        record.setQuestionId(10L);
        record.setSubjectName("=危险科目");
        record.setChapterName("章节");
        record.setKnowledgePointName("知识点");
        record.setType("JUDGE");
        record.setDifficulty(2);
        record.setContent("+危险题干");
        record.setOptions(List.of());
        record.setCorrectAnswer("TRUE");
        record.setAnalysis("@危险解析");
        record.setWrongCount(3);
        record.setLastWrongAt(LocalDateTime.of(2026, 9, 27, 10, 30));
        record.setQuestionStatus(0);
        when(wrongQuestionService.listForExport(eq(7L), any())).thenReturn(List.of(record));

        ExcelExportFile file = service().exportWrongQuestions(7L);

        assertTrue(file.filename().startsWith("我的错题本_"));
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            Sheet sheet = workbook.getSheet("我的错题本");
            DataFormatter formatter = new DataFormatter();
            assertEquals("正确答案", formatter.formatCellValue(sheet.getRow(2).getCell(8)));
            assertEquals("题目解析", formatter.formatCellValue(sheet.getRow(2).getCell(9)));
            assertEquals("'=危险科目", formatter.formatCellValue(sheet.getRow(3).getCell(1)));
            assertEquals("'+危险题干", formatter.formatCellValue(sheet.getRow(3).getCell(6)));
            assertEquals("正确", formatter.formatCellValue(sheet.getRow(3).getCell(8)));
            assertEquals("'@危险解析", formatter.formatCellValue(sheet.getRow(3).getCell(9)));
            assertEquals("已禁用", formatter.formatCellValue(sheet.getRow(3).getCell(12)));
            assertEquals(CellType.STRING, sheet.getRow(3).getCell(6).getCellType());
        }
        verify(wrongQuestionService).listForExport(eq(7L), any());
    }

    @Test
    void favoriteExportOmitsAnswersAndAnalysisAndWritesReadableOptions() throws Exception {
        FavoriteVO record = new FavoriteVO();
        record.setQuestionId(11L);
        record.setSubjectName("Java");
        record.setChapterName("基础");
        record.setKnowledgePointName("集合");
        record.setType("SINGLE");
        record.setDifficulty(1);
        record.setContent("选择安全选项");
        record.setOptions(List.of(new PracticeOptionVO("A", "安全"),
                new PracticeOptionVO("B", "危险")));
        record.setCreatedAt(LocalDateTime.of(2026, 9, 27, 11, 0));
        record.setQuestionStatus(1);
        when(favoriteService.listForExport(eq(8L), any())).thenReturn(List.of(record));

        ExcelExportFile file = service().exportFavorites(8L);

        assertTrue(file.filename().startsWith("我的收藏题目_"));
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            Sheet sheet = workbook.getSheet("我的收藏题目");
            DataFormatter formatter = new DataFormatter();
            List<String> headers = java.util.stream.IntStream.range(0, 10)
                    .mapToObj(index -> formatter.formatCellValue(sheet.getRow(2).getCell(index))).toList();
            assertFalse(headers.contains("正确答案"));
            assertFalse(headers.contains("题目解析"));
            assertEquals("A. 安全\nB. 危险", formatter.formatCellValue(sheet.getRow(3).getCell(7)));
            assertEquals("可练习", formatter.formatCellValue(sheet.getRow(3).getCell(9)));
            assertEquals(10, sheet.getRow(2).getPhysicalNumberOfCells());
        }
        verify(favoriteService).listForExport(eq(8L), any());
    }

    @Test
    void emptyPersonalCollectionsStillProduceValidWorkbooks() throws Exception {
        when(wrongQuestionService.listForExport(eq(9L), any())).thenReturn(List.of());
        when(favoriteService.listForExport(eq(9L), any())).thenReturn(List.of());

        ExcelExportFile wrongFile = service().exportWrongQuestions(9L);
        ExcelExportFile favoriteFile = service().exportFavorites(9L);

        try (Workbook wrong = WorkbookFactory.create(new ByteArrayInputStream(wrongFile.content()));
             Workbook favorite = WorkbookFactory.create(new ByteArrayInputStream(favoriteFile.content()))) {
            assertEquals("暂无数据", wrong.getSheet("我的错题本").getRow(3).getCell(0).getStringCellValue());
            assertEquals("暂无数据", favorite.getSheet("我的收藏题目").getRow(3).getCell(0).getStringCellValue());
        }
    }

    @Test
    void personalRowsFitFourOptionsGrowForLongTextAndHaveBoundedHeight() throws Exception {
        List<PracticeOptionVO> fourOptions = List.of(new PracticeOptionVO("A", "选项一"),
                new PracticeOptionVO("B", "选项二"), new PracticeOptionVO("C", "选项三"),
                new PracticeOptionVO("D", "选项四"));
        var normalWrong = new WrongQuestionVO();
        normalWrong.setContent("短题干");
        normalWrong.setOptions(fourOptions);
        var longWrong = new WrongQuestionVO();
        longWrong.setContent("短题干");
        longWrong.setAnalysis("多行解析\n".repeat(10));
        var hugeWrong = new WrongQuestionVO();
        hugeWrong.setAnalysis("长解析".repeat(5000));
        var normalFavorite = new FavoriteVO();
        normalFavorite.setContent("短题干");
        normalFavorite.setOptions(fourOptions);
        var longFavorite = new FavoriteVO();
        longFavorite.setContent("长题干".repeat(70));
        var hugeFavorite = new FavoriteVO();
        hugeFavorite.setContent("长题干".repeat(5000));
        when(wrongQuestionService.listForExport(eq(10L), any()))
                .thenReturn(List.of(normalWrong, longWrong, hugeWrong));
        when(favoriteService.listForExport(eq(10L), any()))
                .thenReturn(List.of(normalFavorite, longFavorite, hugeFavorite));
        try (var wrong = WorkbookFactory.create(new ByteArrayInputStream(service().exportWrongQuestions(10L).content()));
             var favorite = WorkbookFactory.create(new ByteArrayInputStream(service().exportFavorites(10L).content()))) {
            for (Workbook workbook : List.of(wrong, favorite)) {
                Sheet sheet = workbook.getSheetAt(0);
                assertTrue(sheet.getRow(3).getHeightInPoints() >= 66);
                assertEquals(4, sheet.getRow(3).getCell(7).getStringCellValue().split("\\n").length);
                assertTrue(sheet.getRow(4).getHeightInPoints() > sheet.getRow(3).getHeightInPoints());
                assertTrue(sheet.getRow(5).getHeightInPoints() <= 276);
                assertTrue(sheet.getRow(3).getCell(7).getCellStyle().getWrapText());
                assertTrue(sheet.getPaneInformation().isFreezePane());
                assertTrue(((org.apache.poi.xssf.usermodel.XSSFSheet) sheet).getCTWorksheet().isSetAutoFilter());
            }
        }
    }

    private PersonalQuestionExcelExportService service() {
        return new PersonalQuestionExcelExportService(wrongQuestionService, favoriteService);
    }
}
