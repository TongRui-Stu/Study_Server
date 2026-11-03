package com.tongrui.study.service;

import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.entity.User;
import com.tongrui.study.dto.PersonalQuestionFilter;
import com.tongrui.study.enums.PracticeSource;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import java.io.ByteArrayInputStream;
import com.tongrui.study.mapper.UserMapper;
import com.tongrui.study.vo.FavoriteVO;
import com.tongrui.study.vo.PageVO;
import com.tongrui.study.vo.WrongQuestionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class PersonalQuestionFilterIntegrationTests {

    @Autowired private WrongQuestionService wrongQuestionService;
    @Autowired private FavoriteService favoriteService;
    @Autowired private UserMapper userMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private org.mybatis.spring.SqlSessionTemplate sqlSession;
    @Autowired private PracticeService practiceService;
    @Autowired private PersonalQuestionExcelExportService excelExportService;

    private Candidate first;
    private Candidate sameKnowledgePoint;
    private Candidate sameChapter;
    private Candidate otherChapter;

    @BeforeEach
    void selectCandidates() {
        first = queryOne("""
                SELECT q.id, q.content, q.type, q.difficulty,
                       kp.id AS knowledge_point_id, c.id AS chapter_id, s.id AS subject_id
                FROM question q
                JOIN knowledge_point kp ON kp.id = q.knowledge_point_id
                JOIN chapter c ON c.id = kp.chapter_id
                JOIN subject s ON s.id = c.subject_id
                WHERE q.status = 1 AND kp.status = 1 AND c.status = 1 AND s.status = 1
                  AND CHAR_LENGTH(q.content) <= 80
                ORDER BY q.id
                LIMIT 1
                """);
        sameKnowledgePoint = queryOne("""
                SELECT q.id, q.content, q.type, q.difficulty,
                       kp.id AS knowledge_point_id, c.id AS chapter_id, s.id AS subject_id
                FROM question q
                JOIN knowledge_point kp ON kp.id = q.knowledge_point_id
                JOIN chapter c ON c.id = kp.chapter_id
                JOIN subject s ON s.id = c.subject_id
                WHERE q.status = 1 AND kp.status = 1 AND c.status = 1 AND s.status = 1
                  AND kp.id = ? AND q.id <> ?
                ORDER BY q.id
                LIMIT 1
                """, first.knowledgePointId(), first.questionId());
        sameChapter = queryOne("""
                SELECT q.id, q.content, q.type, q.difficulty,
                       kp.id AS knowledge_point_id, c.id AS chapter_id, s.id AS subject_id
                FROM question q
                JOIN knowledge_point kp ON kp.id = q.knowledge_point_id
                JOIN chapter c ON c.id = kp.chapter_id
                JOIN subject s ON s.id = c.subject_id
                WHERE q.status = 1 AND kp.status = 1 AND c.status = 1 AND s.status = 1
                  AND c.id = ? AND kp.id <> ?
                ORDER BY q.id
                LIMIT 1
                """, first.chapterId(), first.knowledgePointId());
        otherChapter = queryOne("""
                SELECT q.id, q.content, q.type, q.difficulty,
                       kp.id AS knowledge_point_id, c.id AS chapter_id, s.id AS subject_id
                FROM question q
                JOIN knowledge_point kp ON kp.id = q.knowledge_point_id
                JOIN chapter c ON c.id = kp.chapter_id
                JOIN subject s ON s.id = c.subject_id
                WHERE q.status = 1 AND kp.status = 1 AND c.status = 1 AND s.status = 1
                  AND s.id = ? AND c.id <> ?
                ORDER BY q.id
                LIMIT 1
                """, first.subjectId(), first.chapterId());
    }

    @Test
    void wrongQuestionFiltersCombineAndWrongCountSortingIsWhitelisted() {
        Long userId = insertTemporaryUser("wrong");
        Long otherUserId = insertTemporaryUser("wrong_other");
        LocalDateTime base = LocalDateTime.of(2026, 10, 15, 8, 0);
        insertWrong(userId, first.questionId(), 2, base.plusMinutes(2));
        insertWrong(userId, sameKnowledgePoint.questionId(), 5, base.plusMinutes(1));
        insertWrong(userId, sameChapter.questionId(), 1, base.plusMinutes(3));
        insertWrong(userId, otherChapter.questionId(), 3, base.plusMinutes(4));
        insertWrong(otherUserId, first.questionId(), 99, base.plusMinutes(10));

        PageVO<WrongQuestionVO> combined = wrongQuestionService.page(
                1, 20, userId, first.subjectId(), first.chapterId(), first.knowledgePointId(),
                first.type(), first.difficulty(), "  " + first.content() + "  ", null);
        assertEquals(List.of(first.questionId()), questionIds(combined.getRecords()));

        PageVO<WrongQuestionVO> chapter = wrongQuestionService.page(
                1, 20, userId, first.subjectId(), first.chapterId(), null,
                null, null, null, null);
        assertEquals(3, chapter.getTotal());
        assertFalse(questionIds(chapter.getRecords()).contains(otherChapter.questionId()));

        PageVO<WrongQuestionVO> descending = wrongQuestionService.page(
                1, 20, userId, first.subjectId(), null, null,
                null, null, null, "desc");
        assertEquals(List.of(sameKnowledgePoint.questionId(), otherChapter.questionId(),
                first.questionId(), sameChapter.questionId()), questionIds(descending.getRecords()));

        PageVO<WrongQuestionVO> ascending = wrongQuestionService.page(
                1, 20, userId, first.subjectId(), null, null,
                null, null, null, "ASC");
        assertEquals(List.of(sameChapter.questionId(), first.questionId(),
                otherChapter.questionId(), sameKnowledgePoint.questionId()), questionIds(ascending.getRecords()));

        assertThrows(BusinessException.class, () -> wrongQuestionService.page(
                1, 20, userId, null, null, null, null, null, null, "DROP TABLE"));
        assertThrows(BusinessException.class, () -> wrongQuestionService.page(
                1, 20, userId, null, null, null, null, null, "x".repeat(101), null));
    }

    @Test
    void favoriteFiltersCombineAndRemainUserScoped() {
        Long userId = insertTemporaryUser("favorite");
        Long otherUserId = insertTemporaryUser("favorite_other");
        LocalDateTime base = LocalDateTime.of(2026, 10, 15, 9, 0);
        insertFavorite(userId, first.questionId(), base.plusMinutes(1));
        insertFavorite(userId, sameKnowledgePoint.questionId(), base.plusMinutes(2));
        insertFavorite(userId, sameChapter.questionId(), base.plusMinutes(3));
        insertFavorite(userId, otherChapter.questionId(), base.plusMinutes(4));
        insertFavorite(otherUserId, first.questionId(), base.plusMinutes(10));

        PageVO<FavoriteVO> combined = favoriteService.page(
                1, 20, userId, first.subjectId(), first.chapterId(), first.knowledgePointId(),
                first.type(), first.difficulty(), "  " + first.content() + "  ");
        assertEquals(List.of(first.questionId()), favoriteQuestionIds(combined.getRecords()));

        PageVO<FavoriteVO> chapter = favoriteService.page(
                1, 20, userId, first.subjectId(), first.chapterId(), null,
                null, null, null);
        assertEquals(3, chapter.getTotal());
        assertFalse(favoriteQuestionIds(chapter.getRecords()).contains(otherChapter.questionId()));
        assertTrue(chapter.getRecords().stream().allMatch(item -> item.getChapterId().equals(first.chapterId())));

        assertThrows(BusinessException.class, () -> favoriteService.page(
                1, 20, userId, null, null, null, null, null, "x".repeat(101)));
    }

    @Test
    void favoriteTimeOrderingIsSharedByPagesPracticeAndExcelWithStableTies() throws Exception {
        Long userId = insertTemporaryUser("favorite_order");
        Long otherUser = insertTemporaryUser("favorite_order_other");
        LocalDateTime base = LocalDateTime.of(2026, 10, 20, 9, 0);
        // Equal timestamps still have deterministic order through the favorite record ID.
        insertFavorite(userId, first.questionId(), base);
        insertFavorite(userId, sameKnowledgePoint.questionId(), base);
        insertFavorite(userId, sameChapter.questionId(), base.plusMinutes(1));
        insertFavorite(otherUser, otherChapter.questionId(), base.minusMinutes(1));
        List<Long> oldestFirst = List.of(first.questionId(), sameKnowledgePoint.questionId(), sameChapter.questionId());
        for (String order : List.of("ASC", "DESC", "")) {
            List<Long> expected = order.equals("ASC") ? oldestFirst
                    : List.of(sameChapter.questionId(), sameKnowledgePoint.questionId(), first.questionId());
            PersonalQuestionFilter filter = new PersonalQuestionFilter();
            filter.setChapterId(first.chapterId());
            filter.setFavoriteTimeOrder(order.toLowerCase());
            assertEquals(expected, favoriteQuestionIds(favoriteService.listForExport(userId, filter)));
            assertEquals(expected, practiceIds(PracticeSource.FAVORITE, userId, filter));
            assertEquals(expected.subList(0, 2), favoriteQuestionIds(favoriteService.page(
                    1, 2, userId, null, first.chapterId(), null, null, null, null, order).getRecords()));
            assertEquals(expected.subList(2, 3), favoriteQuestionIds(favoriteService.page(
                    2, 2, userId, null, first.chapterId(), null, null, null, null, order).getRecords()));
            try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(
                    excelExportService.exportFavorites(userId, filter).content()))) {
                for (int i = 0; i < expected.size(); i++) {
                    Long id = expected.get(i);
                    String content = jdbcTemplate.queryForObject("SELECT content FROM question WHERE id = ?", String.class, id);
                    assertEquals(content, workbook.getSheetAt(0).getRow(i + 3).getCell(6).getStringCellValue());
                }
            }
        }
        PersonalQuestionFilter invalid = new PersonalQuestionFilter();
        invalid.setFavoriteTimeOrder("ASC; DROP TABLE favorite");
        assertThrows(BusinessException.class, () -> favoriteService.listForExport(userId, invalid));
    }

    @Test
    void filteredPracticeSharesListConditionsAndOrderingAndExcludesDisabledQuestions() {
        Long userId = insertTemporaryUser("practice");
        Long otherUser = insertTemporaryUser("practice_other");
        LocalDateTime base = LocalDateTime.of(2026, 10, 15, 10, 0);
        List<Candidate> candidates = List.of(first, sameKnowledgePoint, sameChapter, otherChapter);
        for (int index = 0; index < candidates.size(); index++) {
            insertWrong(userId, candidates.get(index).questionId(), index + 1, base.plusMinutes(index));
            insertFavorite(userId, candidates.get(index).questionId(), base.plusMinutes(index));
        }
        insertWrong(otherUser, first.questionId(), 99, base);
        insertFavorite(otherUser, first.questionId(), base);
        for (String order : List.of("", "ASC", "DESC")) {
            PersonalQuestionFilter filter = PersonalQuestionFilter.of(first.subjectId(), first.chapterId(),
                    null, null, null, "  ", order);
            List<Long> expected = questionIds(wrongQuestionService.listForExport(userId, filter));
            assertEquals(expected, practiceIds(PracticeSource.WRONG, userId, filter));
            assertEquals(3, expected.size());
        }
        PersonalQuestionFilter precise = PersonalQuestionFilter.of(first.subjectId(), first.chapterId(),
                first.knowledgePointId(), null, null, first.content(), null);
        assertEquals(List.of(first.questionId()), practiceIds(PracticeSource.WRONG, userId, precise));
        assertEquals(List.of(first.questionId()), practiceIds(PracticeSource.FAVORITE, userId, precise));
        assertEquals(4, practiceIds(PracticeSource.WRONG, userId, new PersonalQuestionFilter()).size());
        assertEquals(4, practiceIds(PracticeSource.FAVORITE, userId, new PersonalQuestionFilter()).size());
        jdbcTemplate.update("UPDATE question SET status = 0 WHERE id = ?", first.questionId());
        // JDBC updates bypass MyBatis's transaction-local query cache.
        sqlSession.clearCache();
        assertTrue(practiceIds(PracticeSource.WRONG, userId, precise).isEmpty());
        assertTrue(practiceIds(PracticeSource.FAVORITE, userId, precise).isEmpty());
        // Disabled entries remain exportable, while practice requires enabled hierarchy.
        assertEquals(1, wrongQuestionService.listForExport(userId, precise).size());
        jdbcTemplate.update("UPDATE chapter SET status = 0 WHERE id = ?", otherChapter.chapterId());
        sqlSession.clearCache();
        assertFalse(practiceIds(PracticeSource.WRONG, userId, new PersonalQuestionFilter()).contains(otherChapter.questionId()));
        assertFalse(practiceIds(PracticeSource.FAVORITE, userId, new PersonalQuestionFilter()).contains(otherChapter.questionId()));
    }

    @Test
    void keywordsMatchOnlyQuestionContentAndConflictingHierarchyReturnsEmpty() {
        Long userId = insertTemporaryUser("keyword");
        LocalDateTime base = LocalDateTime.of(2026, 10, 15, 11, 0);
        insertWrong(userId, first.questionId(), 1, base);
        insertFavorite(userId, first.questionId(), base);
        jdbcTemplate.update("UPDATE question_option SET content = ? WHERE question_id = ?",
                "__only_option_marker__", first.questionId());
        PersonalQuestionFilter optionOnly = PersonalQuestionFilter.of(null, null, null, null, null,
                "__only_option_marker__", null);
        assertTrue(practiceIds(PracticeSource.WRONG, userId, optionOnly).isEmpty());
        assertTrue(practiceIds(PracticeSource.FAVORITE, userId, optionOnly).isEmpty());
        assertTrue(wrongQuestionService.listForExport(userId, optionOnly).isEmpty());
        assertTrue(favoriteService.listForExport(userId, optionOnly).isEmpty());
        PersonalQuestionFilter conflict = PersonalQuestionFilter.of(first.subjectId(), otherChapter.chapterId(),
                first.knowledgePointId(), null, null, null, null);
        assertTrue(practiceIds(PracticeSource.WRONG, userId, conflict).isEmpty());
        assertTrue(practiceIds(PracticeSource.FAVORITE, userId, conflict).isEmpty());
    }

    @Test
    void filteredExcelExportsAllMatchesBeyondPaginationAndPreservesFavoritePrivacy() throws Exception {
        Long userId = insertTemporaryUser("export");
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT q.id FROM question q JOIN knowledge_point kp ON kp.id = q.knowledge_point_id "
                + "JOIN chapter c ON c.id = kp.chapter_id WHERE c.subject_id = ? ORDER BY q.id LIMIT 12",
                Long.class, first.subjectId());
        assertEquals(12, ids.size());
        for (int index = 0; index < ids.size(); index++) {
            LocalDateTime time = LocalDateTime.of(2026, 10, 15, 12, 0).plusMinutes(index);
            insertWrong(userId, ids.get(index), index + 1, time);
            insertFavorite(userId, ids.get(index), time);
        }
        PersonalQuestionFilter filter = PersonalQuestionFilter.of(first.subjectId(), null, null,
                null, null, null, "DESC");
        assertEquals(10, wrongQuestionService.page(1, 10, userId, first.subjectId(), null, null,
                null, null, null, "DESC").getRecords().size());
        try (var wrong = WorkbookFactory.create(new ByteArrayInputStream(
                    excelExportService.exportWrongQuestions(userId, filter).content()));
             var favorite = WorkbookFactory.create(new ByteArrayInputStream(
                    excelExportService.exportFavorites(userId, filter).content()))) {
            assertEquals(14, wrong.getSheetAt(0).getLastRowNum());
            assertEquals(12, (int) wrong.getSheetAt(0).getRow(3).getCell(10).getNumericCellValue());
            assertEquals(14, favorite.getSheetAt(0).getLastRowNum());
            assertEquals(10, favorite.getSheetAt(0).getRow(2).getPhysicalNumberOfCells());
            for (var cell : favorite.getSheetAt(0).getRow(2)) {
                assertFalse(List.of("正确答案", "题目解析").contains(cell.getStringCellValue()));
            }
        }
        assertEquals(12, wrongQuestionService.listForExport(userId).size());
        assertEquals(12, favoriteService.listForExport(userId).size());
        filter.setKeyword("__missing_question_marker__");
        try (var empty = WorkbookFactory.create(new ByteArrayInputStream(
                excelExportService.exportFavorites(userId, filter).content()))) {
            assertEquals("暂无数据", empty.getSheetAt(0).getRow(3).getCell(0).getStringCellValue());
        }
    }

    private List<Long> practiceIds(PracticeSource source, Long userId, PersonalQuestionFilter filter) {
        return practiceService.listQuestions(source, null, userId, filter).getQuestions().stream()
                .map(item -> item.getId()).toList();
    }

    private Candidate queryOne(String sql, Object... args) {
        List<Candidate> result = jdbcTemplate.query(sql, (rs, rowNum) -> new Candidate(
                rs.getLong("id"), rs.getString("content"), rs.getString("type"), rs.getInt("difficulty"),
                rs.getLong("knowledge_point_id"),
                rs.getLong("chapter_id"), rs.getLong("subject_id")), args);
        assertEquals(1, result.size(), "演示题库应提供覆盖筛选测试所需的层级数据");
        return result.get(0);
    }

    private Long insertTemporaryUser(String suffix) {
        User user = new User();
        user.setUsername("__filter_" + suffix + "_" + Long.toUnsignedString(System.nanoTime(), 36));
        user.setPassword("not-used");
        user.setNickname("筛选测试用户");
        user.setRole("STUDENT");
        user.setStatus(1);
        userMapper.insert(user);
        return user.getId();
    }

    private void insertWrong(Long userId, Long questionId, int wrongCount, LocalDateTime time) {
        jdbcTemplate.update("""
                INSERT INTO wrong_question (user_id, question_id, wrong_count, last_wrong_at, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, userId, questionId, wrongCount, Timestamp.valueOf(time), Timestamp.valueOf(time));
    }

    private void insertFavorite(Long userId, Long questionId, LocalDateTime time) {
        jdbcTemplate.update("""
                INSERT INTO favorite (user_id, question_id, created_at)
                VALUES (?, ?, ?)
                """, userId, questionId, Timestamp.valueOf(time));
    }

    private List<Long> questionIds(List<WrongQuestionVO> records) {
        return records.stream().map(WrongQuestionVO::getQuestionId).toList();
    }

    private List<Long> favoriteQuestionIds(List<FavoriteVO> records) {
        return records.stream().map(FavoriteVO::getQuestionId).toList();
    }

    private record Candidate(Long questionId, String content, String type, Integer difficulty,
                             Long knowledgePointId,
                             Long chapterId, Long subjectId) {
    }
}
