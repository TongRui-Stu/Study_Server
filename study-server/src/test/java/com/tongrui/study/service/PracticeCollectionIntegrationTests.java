package com.tongrui.study.service;

import com.tongrui.study.entity.User;
import com.tongrui.study.enums.PracticeSource;
import com.tongrui.study.mapper.UserMapper;
import com.tongrui.study.vo.PracticeQuestionListVO;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class PracticeCollectionIntegrationTests {

    @Autowired private PracticeService practiceService;
    @Autowired private UserMapper userMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void personalPracticeCollectionsAreUserScopedOrderedAndSkipDisabledHierarchy() {
        Long currentUserId = insertTemporaryUser("current");
        Long otherUserId = insertTemporaryUser("other");
        List<Long> questionIds = jdbcTemplate.queryForList("""
                SELECT q.id
                FROM question q
                JOIN knowledge_point kp ON kp.id = q.knowledge_point_id
                JOIN chapter c ON c.id = kp.chapter_id
                JOIN subject s ON s.id = c.subject_id
                WHERE q.status = 1 AND kp.status = 1 AND c.status = 1 AND s.status = 1
                ORDER BY q.id
                LIMIT 4
                """, Long.class);
        assertEquals(4, questionIds.size());

        LocalDateTime base = LocalDateTime.of(2026, 9, 27, 8, 0);
        insertWrong(currentUserId, questionIds.get(0), base.plusMinutes(1));
        insertWrong(currentUserId, questionIds.get(1), base.plusMinutes(2));
        insertWrong(currentUserId, questionIds.get(2), base.plusMinutes(3));
        insertWrong(otherUserId, questionIds.get(3), base.plusMinutes(4));
        insertFavorite(currentUserId, questionIds.get(0), base.plusMinutes(1));
        insertFavorite(currentUserId, questionIds.get(1), base.plusMinutes(2));
        insertFavorite(currentUserId, questionIds.get(2), base.plusMinutes(3));
        insertFavorite(otherUserId, questionIds.get(3), base.plusMinutes(4));
        jdbcTemplate.update("UPDATE question SET status = 0 WHERE id = ?", questionIds.get(2));

        PracticeQuestionListVO wrong = practiceService.listQuestions(PracticeSource.WRONG, null, currentUserId);
        PracticeQuestionListVO favorite = practiceService.listQuestions(PracticeSource.FAVORITE, null, currentUserId);

        List<Long> expected = List.of(questionIds.get(1), questionIds.get(0));
        assertEquals(expected, wrong.getQuestions().stream().map(question -> question.getId()).toList());
        assertEquals(expected, favorite.getQuestions().stream().map(question -> question.getId()).toList());
        assertFalse(wrong.getQuestions().stream().map(question -> question.getId()).toList()
                .contains(questionIds.get(3)));
        assertFalse(favorite.getQuestions().stream().map(question -> question.getId()).toList()
                .contains(questionIds.get(3)));
        assertTrue(wrong.getQuestions().stream().allMatch(question -> question.getOptions() != null));
        assertTrue(favorite.getQuestions().stream().allMatch(question -> question.getOptions() != null));
    }

    @Test
    void emptyPersonalCollectionsReturnAnEmptySnapshot() {
        Long userId = insertTemporaryUser("empty");

        PracticeQuestionListVO wrong = practiceService.listQuestions(PracticeSource.WRONG, null, userId);
        PracticeQuestionListVO favorite = practiceService.listQuestions(PracticeSource.FAVORITE, null, userId);

        assertTrue(wrong.getQuestions().isEmpty());
        assertTrue(favorite.getQuestions().isEmpty());
    }

    private Long insertTemporaryUser(String suffix) {
        User user = new User();
        user.setUsername("__pt_" + suffix + "_" + Long.toUnsignedString(System.nanoTime(), 36));
        user.setPassword("not-used");
        user.setNickname("测试用户");
        user.setRole("STUDENT");
        user.setStatus(1);
        userMapper.insert(user);
        return user.getId();
    }

    private void insertWrong(Long userId, Long questionId, LocalDateTime time) {
        jdbcTemplate.update("""
                INSERT INTO wrong_question (user_id, question_id, wrong_count, last_wrong_at, created_at)
                VALUES (?, ?, 1, ?, ?)
                """, userId, questionId, Timestamp.valueOf(time), Timestamp.valueOf(time));
    }

    private void insertFavorite(Long userId, Long questionId, LocalDateTime time) {
        jdbcTemplate.update("""
                INSERT INTO favorite (user_id, question_id, created_at)
                VALUES (?, ?, ?)
                """, userId, questionId, Timestamp.valueOf(time));
    }
}
