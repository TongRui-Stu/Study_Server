package com.tongrui.study.service;

import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.dto.QuestionSaveRequest;
import com.tongrui.study.util.QuestionValidationSupport;
import com.tongrui.study.vo.QuestionImportResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@Transactional
class QuestionImportIntegrationTests {

    private static final String[] HEADERS = {
            "知识点ID", "科目", "章节", "知识点", "题型", "难度", "状态", "题干",
            "选项A", "选项B", "选项C", "选项D", "选项E", "选项F", "选项G", "选项H",
            "正确答案", "解析"
    };

    @Autowired private QuestionImportService service;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean private QuestionImportPersistence persistence;
    private Hierarchy hierarchy;
    private Long adminId;

    @BeforeEach
    void setUp() {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT kp.id AS knowledge_id, kp.name AS knowledge_name,
                       c.name AS chapter_name, s.name AS subject_name
                FROM knowledge_point kp
                JOIN chapter c ON c.id = kp.chapter_id
                JOIN subject s ON s.id = c.subject_id
                ORDER BY kp.id LIMIT 1
                """);
        hierarchy = new Hierarchy(((Number) row.get("knowledge_id")).longValue(),
                String.valueOf(row.get("subject_name")), String.valueOf(row.get("chapter_name")),
                String.valueOf(row.get("knowledge_name")));
        adminId = jdbcTemplate.queryForObject(
                "SELECT id FROM user WHERE role = 'ADMIN' ORDER BY id LIMIT 1", Long.class);
        assertNotNull(adminId);
    }

    @Test
    void importsThreeTypesWithGeneratedIdsAuthenticatedCreatorAndNoLearningData() throws Exception {
        String prefix = "__import_ok_" + Long.toUnsignedString(System.nanoTime(), 36);
        long questionsBefore = count("question");
        long optionsBefore = count("question_option");
        long answersBefore = count("answer_record");
        long wrongBefore = count("wrong_question");
        long favoritesBefore = count("favorite");

        MockMultipartFile file = workbook(prefix, false);
        QuestionImportResult result = service.execute(file, adminId);

        assertEquals(3, result.questionCount());
        assertEquals(5, result.optionCount());
        assertEquals(questionsBefore + 3, count("question"));
        assertEquals(optionsBefore + 5, count("question_option"));
        List<Map<String, Object>> inserted = jdbcTemplate.queryForList(
                "SELECT id, created_by FROM question WHERE content LIKE ? ORDER BY id", prefix + "%");
        assertEquals(3, inserted.size());
        inserted.forEach(row -> {
            assertNotNull(row.get("id"));
            assertEquals(adminId.longValue(), ((Number) row.get("created_by")).longValue());
        });
        assertEquals(answersBefore, count("answer_record"));
        assertEquals(wrongBefore, count("wrong_question"));
        assertEquals(favoritesBefore, count("favorite"));
    }

    @Test
    void invalidRowAndRepeatedFileBothAddZeroRows() throws Exception {
        String invalidPrefix = "__import_bad_" + Long.toUnsignedString(System.nanoTime(), 36);
        MockMultipartFile invalid = workbook(invalidPrefix, true);
        long beforeInvalid = count("question");
        assertThrows(BusinessException.class, () -> service.execute(invalid, adminId));
        assertEquals(beforeInvalid, count("question"));

        String duplicatePrefix = "__import_dup_" + Long.toUnsignedString(System.nanoTime(), 36);
        byte[] validBytes = workbook(duplicatePrefix, false).getBytes();
        service.execute(upload(validBytes), adminId);
        long afterFirst = count("question");
        assertThrows(BusinessException.class, () -> service.execute(upload(validBytes), adminId));
        assertEquals(afterFirst, count("question"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void unexpectedFailureDuringSecondInsertRollsBackFirstInsertAndOptions() throws Exception {
        String prefix = "__import_rollback_" + Long.toUnsignedString(System.nanoTime(), 36);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) throw new IllegalStateException("simulated second-row insert failure");
            return invocation.callRealMethod();
        }).when(persistence).persist(any(QuestionSaveRequest.class),
                any(QuestionValidationSupport.PreparedQuestion.class), any(Long.class));

        assertThrows(IllegalStateException.class, () -> service.execute(workbook(prefix, false), adminId));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question WHERE content LIKE ?", Long.class, prefix + "%"));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM question_option qo
                JOIN question q ON q.id = qo.question_id
                WHERE q.content LIKE ?
                """, Long.class, prefix + "%"));
    }

    private MockMultipartFile workbook(String prefix, boolean invalidSecondRow) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet input = workbook.createSheet("题目录入");
        Row header = input.createRow(0);
        for (int index = 0; index < HEADERS.length; index++) header.createCell(index).setCellValue(HEADERS[index]);
        writeRow(input.createRow(1), "SINGLE", prefix + "_single", List.of("甲", "乙"), "A");
        writeRow(input.createRow(2), "MULTIPLE", prefix + "_multiple", List.of("甲", "乙", "丙"),
                invalidSecondRow ? "A" : "A,C");
        writeRow(input.createRow(3), "JUDGE", prefix + "_judge", List.of(), "TRUE");
        workbook.createSheet("填写示例");
        workbook.createSheet("填写说明");
        workbook.createSheet("知识点清单");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (workbook; output) {
            workbook.write(output);
        }
        return upload(output.toByteArray());
    }

    private void writeRow(Row row, String type, String content, List<String> options, String answer) {
        row.createCell(0).setCellValue(hierarchy.knowledgePointId());
        row.createCell(1).setCellValue(hierarchy.subjectName());
        row.createCell(2).setCellValue(hierarchy.chapterName());
        row.createCell(3).setCellValue(hierarchy.knowledgePointName());
        row.createCell(4).setCellValue(type);
        row.createCell(5).setCellValue("2");
        row.createCell(6).setCellValue("1");
        row.createCell(7).setCellValue(content);
        for (int index = 0; index < options.size(); index++) row.createCell(8 + index).setCellValue(options.get(index));
        row.createCell(16).setCellValue(answer);
        row.createCell(17).setCellValue("integration test");
    }

    private MockMultipartFile upload(byte[] bytes) {
        return new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private record Hierarchy(Long knowledgePointId, String subjectName,
                             String chapterName, String knowledgePointName) {
    }
}
