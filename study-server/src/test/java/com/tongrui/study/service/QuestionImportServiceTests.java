package com.tongrui.study.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.entity.Chapter;
import com.tongrui.study.entity.KnowledgePoint;
import com.tongrui.study.entity.Question;
import com.tongrui.study.entity.Subject;
import com.tongrui.study.mapper.ChapterMapper;
import com.tongrui.study.mapper.KnowledgePointMapper;
import com.tongrui.study.mapper.QuestionMapper;
import com.tongrui.study.mapper.SubjectMapper;
import com.tongrui.study.vo.ExcelExportFile;
import com.tongrui.study.vo.QuestionImportResult;
import com.tongrui.study.vo.QuestionImportValidationResult;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionImportServiceTests {

    private static final String[] HEADERS = {
            "知识点ID", "科目", "章节", "知识点", "题型", "难度", "状态", "题干",
            "选项A", "选项B", "选项C", "选项D", "选项E", "选项F", "选项G", "选项H",
            "正确答案", "解析"
    };

    @Mock private SubjectMapper subjectMapper;
    @Mock private ChapterMapper chapterMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionImportPersistence persistence;
    private QuestionImportService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Question.class);
        service = new QuestionImportService(subjectMapper, chapterMapper, knowledgePointMapper,
                questionMapper, persistence);
        useHierarchy(1, 1, 1);
        lenient().when(questionMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void templateUsesBlankInputLayoutCascadeDropdownsFormattingAndYaHeiFonts() throws Exception {
        ExcelExportFile file = service.createTemplate();
        QuestionImportValidationResult blankTemplateResult = service.validate(new MockMultipartFile(
                "file", file.filename(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file.content()));
        assertTrue(blankTemplateResult.valid());
        assertEquals(0, blankTemplateResult.totalRows());

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            assertEquals(List.of("题目录入", "填写示例", "填写说明", "知识点清单"),
                    java.util.stream.IntStream.range(0, workbook.getNumberOfSheets())
                            .mapToObj(workbook::getSheetName).toList());
            Sheet input = workbook.getSheet("题目录入");
            assertTrue(input.getRow(0).getCell(0).getStringCellValue().contains("第 3 行"));
            assertEquals(new CellRangeAddress(0, 0, 0, 17), input.getMergedRegion(0));
            for (int index = 0; index < HEADERS.length; index++) {
                assertEquals(HEADERS[index], input.getRow(1).getCell(index).getStringCellValue());
            }
            assertNull(input.getRow(2));
            assertYaHei(workbook, 0);
            assertEquals(org.apache.poi.ss.usermodel.FontScheme.NONE,
                    ((org.apache.poi.xssf.usermodel.XSSFFont) workbook.getFontAt(0)).getScheme());
            for (int column = 0; column < HEADERS.length; column++) {
                assertNotNull(input.getColumnStyle(column));
                assertYaHei(workbook, input.getColumnStyle(column).getFontIndex());
            }
            for (int column : new int[]{7, 8, 15, 17}) {
                assertTrue(input.getColumnStyle(column).getWrapText());
            }
            // No data row materialization: row 3 and row 1002 inherit column styles.
            assertNull(input.getRow(1001));
            for (Sheet templateSheet : workbook) {
                assertYaHei(workbook, templateSheet.getWorkbook().getCellStyleAt(0).getFontIndex());
            }
            assertTrue(input.getPaneInformation().isFreezePane());
            assertEquals(2, input.getPaneInformation().getHorizontalSplitTopRow());
            assertEquals("A2:R1002", ((XSSFSheet) input).getCTWorksheet().getAutoFilter().getRef());
            assertTrue(input.isColumnHidden(0));
            assertEquals(6, input.getDataValidations().size());
            for (DataValidation validation : input.getDataValidations()) {
                CellRangeAddressList regions = validation.getRegions();
                assertEquals(1, regions.countRanges());
                assertEquals(2, regions.getCellRangeAddress(0).getFirstRow());
                assertEquals(1001, regions.getCellRangeAddress(0).getLastRow());
                assertFalse(validation.getShowPromptBox());
            }
            assertTrue(input.getDataValidations().stream().noneMatch(validation ->
                    validation.getRegions().getCellRangeAddress(0).getFirstColumn() == 0));
            assertEquals("ValidSubjects", validationForColumn(input, 1)
                    .getValidationConstraint().getFormula1());
            assertTrue(validationForColumn(input, 2).getValidationConstraint().getFormula1()
                    .contains("INDIRECT(IF($B3="));
            assertTrue(validationForColumn(input, 2).getValidationConstraint().getFormula1()
                    .contains("ChaptersForSubject_"));
            assertTrue(validationForColumn(input, 3).getValidationConstraint().getFormula1()
                    .contains("INDIRECT(IF($C3="));
            assertTrue(validationForColumn(input, 3).getValidationConstraint().getFormula1()
                    .contains("KnowledgeForChapter_"));
            assertNotNull(validationForColumn(input, 4));
            assertNotNull(validationForColumn(input, 5));
            assertNotNull(validationForColumn(input, 6));
            assertNotNull(workbook.getName("ValidSubjects"));
            assertNotNull(workbook.getName("ChaptersForSubject_1"));
            assertNotNull(workbook.getName("KnowledgeForChapter_1"));
            assertNotNull(workbook.getName("EmptyHierarchyOptions"));
            assertNull(workbook.getName("ValidKnowledgePointIds"));
            assertFalse(java.util.Arrays.asList(HEADERS).contains("题目ID"));
            assertFalse(java.util.Arrays.asList(HEADERS).contains("选项ID"));
            assertYaHei(workbook, input.getRow(0).getCell(0).getCellStyle().getFontIndex());
            assertYaHei(workbook, input.getRow(1).getCell(0).getCellStyle().getFontIndex());

            Sheet examples = workbook.getSheet("填写示例");
            assertNull(examples.getRow(1).getCell(0));
            assertEquals("1｜408", examples.getRow(1).getCell(1).getStringCellValue());
            assertEquals("1｜数据结构", examples.getRow(1).getCell(2).getStringCellValue());
            assertEquals("1｜线性表", examples.getRow(1).getCell(3).getStringCellValue());
            assertEquals("单选题", examples.getRow(1).getCell(4).getStringCellValue());
            assertEquals("多选题", examples.getRow(2).getCell(4).getStringCellValue());
            assertEquals("判断题", examples.getRow(3).getCell(4).getStringCellValue());
            Sheet knowledge = workbook.getSheet("知识点清单");
            assertEquals("知识点ID", knowledge.getRow(0).getCell(0).getStringCellValue());
            assertEquals(1D, knowledge.getRow(1).getCell(0).getNumericCellValue());
            assertEquals("408", knowledge.getRow(1).getCell(1).getStringCellValue());
            assertTrue(knowledge.isColumnHidden(7));
            assertTrue(knowledge.isColumnHidden(8));
            assertTrue(knowledge.isColumnHidden(9));
            assertTrue(knowledge.isColumnHidden(10));
            Sheet instructions = workbook.getSheet("填写说明");
            assertTrue(instructions.getRow(2).getCell(1).getStringCellValue()
                    .contains("只读取“题目录入”"));
            assertTrue(allInstructionText(instructions).contains("\\(2^6=64\\)"));
            assertTrue(allInstructionText(instructions).contains("\\[a^2+b^2=c^2\\]"));
            assertTrue(allInstructionText(instructions).contains("不带分隔符的2^6"));
            assertTrue(allInstructionText(instructions).contains("ID｜名称"));
            assertTrue(allInstructionText(instructions).contains("不会自动清除"));
            assertTrue(allInstructionText(instructions).contains("旧模板"));
            assertYaHei(workbook, examples.getRow(1).getCell(7).getCellStyle().getFontIndex());
            assertYaHei(workbook, instructions.getRow(0).getCell(0).getCellStyle().getFontIndex());
            assertYaHei(workbook, instructions.getRow(2).getCell(1).getCellStyle().getFontIndex());
            assertYaHei(workbook, knowledge.getRow(1).getCell(1).getCellStyle().getFontIndex());
        }
    }

    @Test
    void templateRemainsReadableWithoutKnowledgePointsAndExampleSheetIsNeverImported() throws Exception {
        when(knowledgePointMapper.selectList(null)).thenReturn(List.of());
        ExcelExportFile file = service.createTemplate();
        MockMultipartFile upload = new MockMultipartFile("file", file.filename(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file.content());

        QuestionImportValidationResult result = service.validate(upload);

        assertTrue(result.valid());
        assertEquals(0, result.totalRows());
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            assertTrue(workbook.getSheet("知识点清单").getRow(1).getCell(0).getStringCellValue().contains("暂无知识点"));
            assertNull(workbook.getName("ValidSubjects"));
            assertNull(workbook.getName("EmptyHierarchyOptions"));
            assertEquals(3, workbook.getSheet("题目录入").getDataValidations().size());
            assertTrue(workbook.getSheet("题目录入").getDataValidations().stream().allMatch(validation -> {
                int column = validation.getRegions().getCellRangeAddress(0).getFirstColumn();
                return column >= 4 && column <= 6;
            }));
            assertTrue(allInstructionText(workbook.getSheet("填写说明")).contains("先创建科目、章节和知识点"));
        }
    }

    @Test
    void cascadeDropdownContainsOnlyActivePathsWhileKnowledgeSheetKeepsDisabledRows() throws Exception {
        KnowledgePoint disabled = new KnowledgePoint();
        disabled.setId(2L);
        disabled.setChapterId(1L);
        disabled.setName("禁用知识点");
        disabled.setStatus(0);
        KnowledgePoint active = new KnowledgePoint();
        active.setId(1L);
        active.setChapterId(1L);
        active.setName("线性表");
        active.setStatus(1);
        when(knowledgePointMapper.selectList(null)).thenReturn(List.of(disabled, active));

        ExcelExportFile file = service.createTemplate();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            Sheet knowledge = workbook.getSheet("知识点清单");
            assertEquals(1D, knowledge.getRow(1).getCell(0).getNumericCellValue());
            assertEquals(2D, knowledge.getRow(2).getCell(0).getNumericCellValue());
            assertEquals("禁用", knowledge.getRow(2).getCell(6).getStringCellValue());
            assertEquals("'知识点清单'!$H$2:$H$2",
                    workbook.getName("ValidSubjects").getRefersToFormula());
            assertEquals("'知识点清单'!$J$2:$J$2",
                    workbook.getName("ChaptersForSubject_1").getRefersToFormula());
            assertEquals("'知识点清单'!$K$2:$K$2",
                    workbook.getName("KnowledgeForChapter_1").getRefersToFormula());
            assertEquals("1｜线性表", knowledge.getRow(1).getCell(10).getStringCellValue());
            assertFalse(workbook.getName("KnowledgeForChapter_1").getRefersToFormula().contains("$K$3"));
        }
    }

    @Test
    void newTemplateDerivesKnowledgeIdFromCascadeSelectionStartingOnThirdRow() throws Exception {
        Workbook workbook = generatedTemplateWorkbook();
        writeRow(workbook, 2, "", "1｜408", "1｜数据结构", "1｜线性表", "单选题", "中等", "启用",
                "新模板单选", List.of("甲", "乙"), "A", "解析");
        writeRow(workbook, 3, "1", "1｜408", "1｜数据结构", "1｜线性表", "MULTIPLE", "1", "1",
                "新模板多选", List.of("甲", "乙", "丙"), "A,C", "");
        writeRow(workbook, 4, "", "1｜408", "1｜数据结构", "1｜线性表", "判断题", "困难", "禁用",
                "新模板判断", List.of(), "错误", "");

        QuestionImportValidationResult result = service.validate(upload(workbook));

        assertTrue(result.valid());
        assertEquals(3, result.totalRows());
        assertEquals(List.of(3, 4, 5), result.previewRows().stream().map(row -> row.rowNumber()).toList());
        assertEquals(List.of("SINGLE", "MULTIPLE", "JUDGE"),
                result.previewRows().stream().map(row -> row.type()).toList());
        assertEquals(List.of(1L, 1L, 1L),
                result.previewRows().stream().map(row -> row.knowledgePointId()).toList());
    }

    @Test
    void cascadeNamedRangesUseIdsAndKeepSameNamesInTheirOwnBranches() throws Exception {
        useTwoActiveBranchesWithSameNames();

        ExcelExportFile file = service.createTemplate();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            Sheet knowledge = workbook.getSheet("知识点清单");
            assertEquals("'知识点清单'!$H$2:$H$3",
                    workbook.getName("ValidSubjects").getRefersToFormula());
            assertEquals("1｜408", knowledge.getRow(1).getCell(7).getStringCellValue());
            assertEquals("2｜数学", knowledge.getRow(2).getCell(7).getStringCellValue());
            assertEquals("'知识点清单'!$J$2:$J$2",
                    workbook.getName("ChaptersForSubject_1").getRefersToFormula());
            assertEquals("'知识点清单'!$K$2:$K$2",
                    workbook.getName("ChaptersForSubject_2").getRefersToFormula());
            assertEquals("10｜基础", knowledge.getRow(1).getCell(9).getStringCellValue());
            assertEquals("20｜基础", knowledge.getRow(1).getCell(10).getStringCellValue());
            assertEquals("'知识点清单'!$L$2:$L$2",
                    workbook.getName("KnowledgeForChapter_10").getRefersToFormula());
            assertEquals("'知识点清单'!$M$2:$M$2",
                    workbook.getName("KnowledgeForChapter_20").getRefersToFormula());
            assertEquals("100｜绪论", knowledge.getRow(1).getCell(11).getStringCellValue());
            assertEquals("200｜绪论", knowledge.getRow(1).getCell(12).getStringCellValue());
            assertNull(workbook.getName("ChaptersForSubject_3"));
            assertNull(workbook.getName("ChaptersForSubject_1_基础"));
            for (int column = 7; column <= 12; column++) assertTrue(knowledge.isColumnHidden(column));
        }
    }

    @Test
    void rejectsLegacyDropdownIdMismatchAndCrossBranchSelectionsWithPreciseFields() throws Exception {
        useTwoActiveBranchesWithSameNames();
        Workbook workbook = emptyImportWorkbook();
        writeRow(workbook, 1, "100", "1｜408", "10｜基础", "200｜绪论", "单选题", "中等", "启用",
                "ID不一致", List.of("甲", "乙"), "A", "");
        writeRow(workbook, 2, "", "1｜408", "20｜基础", "200｜绪论", "单选题", "中等", "启用",
                "章节串层级", List.of("甲", "乙"), "A", "");
        writeRow(workbook, 3, "", "2｜数学", "20｜基础", "100｜绪论", "单选题", "中等", "启用",
                "知识点串层级", List.of("甲", "乙"), "A", "");
        writeRow(workbook, 4, "", "1｜错误名称", "10｜基础", "100｜绪论", "单选题", "中等", "启用",
                "名称不一致", List.of("甲", "乙"), "A", "");

        QuestionImportValidationResult result = service.validate(upload(workbook));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(issue -> issue.rowNumber() == 2
                && issue.field().equals("知识点ID") && issue.message().contains("下拉选项不一致")));
        assertTrue(result.errors().stream().anyMatch(issue -> issue.rowNumber() == 3
                && issue.field().equals("章节") && issue.message().contains("不属于所选科目")));
        assertTrue(result.errors().stream().anyMatch(issue -> issue.rowNumber() == 4
                && issue.field().equals("知识点") && issue.message().contains("不属于所选章节")));
        assertTrue(result.errors().stream().anyMatch(issue -> issue.rowNumber() == 5
                && issue.field().equals("科目") && issue.message().contains("名称与ID不一致")));
    }

    @Test
    void hierarchyOptionSplitsOnlyAtFirstFullWidthSeparator() throws Exception {
        Subject subject = subject(1L, "408", 1);
        Chapter chapter = chapter(1L, 1L, "数据结构｜基础", 1);
        KnowledgePoint knowledge = knowledge(1L, 1L, "线性｜表", 1);
        when(subjectMapper.selectList(null)).thenReturn(List.of(subject));
        when(chapterMapper.selectList(null)).thenReturn(List.of(chapter));
        when(knowledgePointMapper.selectList(null)).thenReturn(List.of(knowledge));
        Workbook workbook = emptyImportWorkbook();
        writeRow(workbook, 1, "", "1｜408", "1｜数据结构｜基础", "1｜线性｜表",
                "单选题", "中等", "启用", "分隔符名称", List.of("甲", "乙"), "A", "");

        QuestionImportValidationResult result = service.validate(upload(workbook));

        assertTrue(result.valid());
        assertEquals(1L, result.previewRows().get(0).knowledgePointId());
    }

    @Test
    void oldFirstRowHeaderStillValidatesAndNormalizesRows() throws Exception {
        Workbook workbook = emptyImportWorkbook();
        writeRow(workbook, 1, "1", "408", "数据结构", "线性表", "单选题", "中等", "启用",
                " 唯一单选题 ", List.of("甲", "乙"), "a", "解析");
        writeRow(workbook, 2, "1", "408", "数据结构", "线性表", "MULTIPLE", "1", "1",
                "唯一多选题", List.of("甲", "乙", "丙"), " c， a ", "");
        writeRow(workbook, 3, "1", "", "", "", "判断题", "困难", "禁用",
                "唯一判断题", List.of(), "正确", "");
        writeRow(workbook, 4, "1", "408", "数据结构", "线性表", "", "", "", "", List.of(), "", "");

        QuestionImportValidationResult result = service.validate(upload(workbook));

        assertTrue(result.valid());
        assertEquals(3, result.totalRows());
        assertEquals(3, result.validRows());
        assertEquals(List.of("A", "A,C", "TRUE"),
                result.previewRows().stream().map(row -> row.answer()).toList());
        assertEquals(List.of("SINGLE", "MULTIPLE", "JUDGE"),
                result.previewRows().stream().map(row -> row.type()).toList());
    }

    @Test
    void reportsPreciseRowsForGapMissingAnswerJudgeOptionsHierarchyMismatchAndDuplicates() throws Exception {
        Workbook workbook = emptyImportWorkbook();
        writeRow(workbook, 1, "999", "错误科目", "数据结构", "线性表", "单选题", "中等", "启用",
                "不存在层级", List.of("A", "B"), "A", "");
        writeRow(workbook, 2, "1", "错误科目", "数据结构", "线性表", "单选题", "中等", "启用",
                "名称错误", List.of("A", "B"), "A", "");
        writeRow(workbook, 3, "1", "408", "数据结构", "线性表", "判断题", "中等", "启用",
                "判断题含选项", List.of("A", "B"), "FALSE", "");
        writeRow(workbook, 4, "1", "408", "数据结构", "线性表", "多选题", "中等", "启用",
                "答案不存在", List.of("A", "B"), "A,C", "");
        writeRow(workbook, 5, "1", "408", "数据结构", "线性表", "单选题", "中等", "启用",
                "文件重复", List.of("A", "B"), "A", "");
        writeRow(workbook, 6, "1", "408", "数据结构", "线性表", "单选题", "中等", "启用",
                "文件重复", List.of("A", "B"), "A", "");
        workbook.getSheet("题目录入").getRow(4).getCell(9).setBlank();
        workbook.getSheet("题目录入").getRow(4).createCell(10).setCellValue("C");

        QuestionImportValidationResult result = service.validate(upload(workbook));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(issue -> issue.rowNumber() == 2 && issue.message().contains("知识点ID不存在")));
        assertTrue(result.errors().stream().anyMatch(issue -> issue.rowNumber() == 3 && issue.message().contains("层级不一致")));
        assertTrue(result.errors().stream().anyMatch(issue -> issue.rowNumber() == 4 && issue.message().contains("判断题不能填写选项")));
        assertTrue(result.errors().stream().anyMatch(issue -> issue.rowNumber() == 5 && issue.message().contains("中间断档")));
        assertTrue(result.errors().stream().anyMatch(issue -> issue.rowNumber() == 7 && issue.message().contains("文件内")));
        assertEquals(1, result.duplicateRows());
    }

    @Test
    void rejectsDatabaseDuplicateFormulaWrongHeaderWrongSheetAndCorruptFiles() throws Exception {
        Question existing = new Question();
        existing.setKnowledgePointId(1L);
        existing.setContent("数据库重复");
        when(questionMapper.selectList(any())).thenReturn(List.of(existing));
        Workbook duplicateWorkbook = emptyImportWorkbook();
        writeRow(duplicateWorkbook, 1, "1", "408", "数据结构", "线性表", "单选题", "中等", "启用",
                "数据库重复", List.of("A", "B"), "A", "");
        QuestionImportValidationResult duplicate = service.validate(upload(duplicateWorkbook));
        assertFalse(duplicate.valid());
        assertTrue(duplicate.errors().get(0).message().contains("已存在"));

        Workbook formulaWorkbook = emptyImportWorkbook();
        writeRow(formulaWorkbook, 1, "1", "408", "数据结构", "线性表", "单选题", "中等", "启用",
                "公式", List.of("A", "B"), "A", "");
        formulaWorkbook.getSheet("题目录入").getRow(1).getCell(7).setCellFormula("1+1");
        assertTrue(service.validate(upload(formulaWorkbook)).errors().get(0).message().contains("公式"));

        Workbook headerWorkbook = emptyImportWorkbook();
        headerWorkbook.getSheet("题目录入").getRow(0).getCell(7).setCellValue("错误表头");
        BusinessException headerException = assertThrows(BusinessException.class,
                () -> service.validate(upload(headerWorkbook)));
        assertTrue(headerException.getMessage().contains("第1行或第2行"));

        Workbook sheetWorkbook = emptyImportWorkbook();
        sheetWorkbook.setSheetName(1, "错误名称");
        assertThrows(BusinessException.class, () -> service.validate(upload(sheetWorkbook)));

        MockMultipartFile corrupt = new MockMultipartFile("file", "broken.xlsx",
                "application/octet-stream", "not-an-xlsx".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(BusinessException.class, () -> service.validate(corrupt));
    }

    @Test
    void warnsForDisabledHierarchyAndRejectsFileTypeEmptyAndOversize() throws Exception {
        useHierarchy(1, 0, 1);
        Workbook workbook = emptyImportWorkbook();
        writeRow(workbook, 1, "1", "408", "数据结构", "线性表", "SINGLE", "2", "1",
                "禁用层级题目", List.of("A", "B"), "A", "");
        QuestionImportValidationResult result = service.validate(upload(workbook));
        assertTrue(result.valid());
        assertEquals(1, result.warningCount());

        assertThrows(BusinessException.class, () -> service.validate(
                new MockMultipartFile("file", "old.xls", "application/octet-stream", new byte[]{1})));
        assertThrows(BusinessException.class, () -> service.validate(
                new MockMultipartFile("file", "empty.xlsx", "application/octet-stream", new byte[0])));
        assertThrows(BusinessException.class, () -> service.validate(
                new MockMultipartFile("file", "large.xlsx", "application/octet-stream",
                        new byte[(int) QuestionImportService.MAX_FILE_BYTES + 1])));
    }

    @Test
    void rejectsMoreThanOneThousandEffectiveQuestions() throws Exception {
        Workbook workbook = emptyImportWorkbook();
        for (int index = 1; index <= QuestionImportService.MAX_QUESTION_ROWS + 1; index++) {
            writeRow(workbook, index, "1", "408", "数据结构", "线性表", "SINGLE", "2", "1",
                    "批量题目" + index, List.of("A", "B"), "A", "");
        }

        QuestionImportValidationResult result = service.validate(upload(workbook));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(issue -> issue.message().contains("1000")));
    }

    @Test
    void executeRevalidatesUsesGeneratedIdsAndAuthenticatedCreator() throws Exception {
        Workbook workbook = emptyImportWorkbook();
        writeRow(workbook, 1, "1", "408", "数据结构", "线性表", "SINGLE", "2", "1",
                "导入单选", List.of("A", "B"), "A", "");
        writeRow(workbook, 2, "1", "408", "数据结构", "线性表", "MULTIPLE", "1", "1",
                "导入多选", List.of("A", "B", "C"), "A,C", "");
        writeRow(workbook, 3, "1", "408", "数据结构", "线性表", "JUDGE", "3", "0",
                "导入判断", List.of(), "FALSE", "");
        when(persistence.persist(any(), any(), org.mockito.ArgumentMatchers.eq(99L)))
                .thenAnswer(invocation -> ((com.tongrui.study.util.QuestionValidationSupport.PreparedQuestion)
                        invocation.getArgument(1)).options().size());

        QuestionImportResult result = service.execute(upload(workbook), 99L);

        assertEquals(3, result.questionCount());
        assertEquals(5, result.optionCount());
        verify(persistence, org.mockito.Mockito.times(3)).persist(any(), any(), org.mockito.ArgumentMatchers.eq(99L));
    }

    @Test
    void executeWithAnyValidationErrorPerformsZeroWrites() throws Exception {
        Workbook workbook = emptyImportWorkbook();
        writeRow(workbook, 1, "1", "408", "数据结构", "线性表", "MULTIPLE", "2", "1",
                "错误多选", List.of("A", "B"), "A", "");

        assertThrows(BusinessException.class, () -> service.execute(upload(workbook), 99L));
        verify(persistence, never()).persist(any(), any(), any());
    }

    private void useHierarchy(int subjectStatus, int chapterStatus, int knowledgeStatus) {
        Subject subject = subject(1L, "408", subjectStatus);
        Chapter chapter = chapter(1L, 1L, "数据结构", chapterStatus);
        KnowledgePoint knowledge = knowledge(1L, 1L, "线性表", knowledgeStatus);
        lenient().when(subjectMapper.selectList(null)).thenReturn(List.of(subject));
        lenient().when(chapterMapper.selectList(null)).thenReturn(List.of(chapter));
        lenient().when(knowledgePointMapper.selectList(null)).thenReturn(List.of(knowledge));
    }

    private void useTwoActiveBranchesWithSameNames() {
        Subject first = subject(1L, "408", 1);
        Subject second = subject(2L, "数学", 1);
        Subject disabled = subject(3L, "禁用科目", 0);
        Chapter firstChapter = chapter(10L, 1L, "基础", 1);
        Chapter secondChapter = chapter(20L, 2L, "基础", 1);
        Chapter disabledChapter = chapter(30L, 3L, "基础", 1);
        KnowledgePoint firstKnowledge = knowledge(100L, 10L, "绪论", 1);
        KnowledgePoint secondKnowledge = knowledge(200L, 20L, "绪论", 1);
        KnowledgePoint disabledKnowledge = knowledge(300L, 30L, "绪论", 1);
        when(subjectMapper.selectList(null)).thenReturn(List.of(first, second, disabled));
        when(chapterMapper.selectList(null)).thenReturn(List.of(
                firstChapter, secondChapter, disabledChapter));
        when(knowledgePointMapper.selectList(null)).thenReturn(List.of(
                firstKnowledge, secondKnowledge, disabledKnowledge));
    }

    private Subject subject(long id, String name, int status) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setName(name);
        subject.setStatus(status);
        return subject;
    }

    private Chapter chapter(long id, long subjectId, String name, int status) {
        Chapter chapter = new Chapter();
        chapter.setId(id);
        chapter.setSubjectId(subjectId);
        chapter.setName(name);
        chapter.setStatus(status);
        return chapter;
    }

    private KnowledgePoint knowledge(long id, long chapterId, String name, int status) {
        KnowledgePoint knowledge = new KnowledgePoint();
        knowledge.setId(id);
        knowledge.setChapterId(chapterId);
        knowledge.setName(name);
        knowledge.setStatus(status);
        return knowledge;
    }

    private Workbook emptyImportWorkbook() {
        Workbook workbook = new XSSFWorkbook();
        Sheet input = workbook.createSheet("题目录入");
        org.apache.poi.ss.usermodel.Row header = input.createRow(0);
        for (int index = 0; index < HEADERS.length; index++) header.createCell(index).setCellValue(HEADERS[index]);
        workbook.createSheet("填写示例");
        workbook.createSheet("填写说明");
        workbook.createSheet("知识点清单");
        return workbook;
    }

    private Workbook generatedTemplateWorkbook() throws Exception {
        ExcelExportFile file = service.createTemplate();
        return WorkbookFactory.create(new ByteArrayInputStream(file.content()));
    }

    private void writeRow(Workbook workbook, int rowIndex, String id, String subject, String chapter,
                          String knowledge, String type, String difficulty, String status, String content,
                          List<String> options, String answer, String analysis) {
        org.apache.poi.ss.usermodel.Row row = workbook.getSheet("题目录入").createRow(rowIndex);
        String[] fixed = {id, subject, chapter, knowledge, type, difficulty, status, content};
        for (int index = 0; index < fixed.length; index++) row.createCell(index).setCellValue(fixed[index]);
        for (int index = 0; index < options.size(); index++) row.createCell(8 + index).setCellValue(options.get(index));
        row.createCell(16).setCellValue(answer);
        row.createCell(17).setCellValue(analysis);
    }

    private MockMultipartFile upload(Workbook workbook) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (workbook; output) {
            workbook.write(output);
        }
        return new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
    }

    private String allInstructionText(Sheet sheet) {
        DataFormatter formatter = new DataFormatter();
        StringBuilder text = new StringBuilder();
        for (org.apache.poi.ss.usermodel.Row row : sheet) {
            for (org.apache.poi.ss.usermodel.Cell cell : row) {
                text.append(formatter.formatCellValue(cell)).append('\n');
            }
        }
        return text.toString();
    }

    private DataValidation validationForColumn(Sheet sheet, int column) {
        return sheet.getDataValidations().stream()
                .filter(validation -> validation.getRegions().getCellRangeAddress(0).getFirstColumn() == column)
                .findFirst().orElseThrow();
    }

    private void assertYaHei(Workbook workbook, int fontIndex) {
        Font font = workbook.getFontAt(fontIndex);
        assertEquals("Microsoft YaHei", font.getFontName());
    }
}
