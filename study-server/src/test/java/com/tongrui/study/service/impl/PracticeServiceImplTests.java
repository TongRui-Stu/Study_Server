package com.tongrui.study.service.impl;

import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.entity.Chapter;
import com.tongrui.study.entity.KnowledgePoint;
import com.tongrui.study.entity.Question;
import com.tongrui.study.entity.QuestionOption;
import com.tongrui.study.entity.Subject;
import com.tongrui.study.enums.PracticeSource;
import com.tongrui.study.mapper.AnswerRecordMapper;
import com.tongrui.study.mapper.ChapterMapper;
import com.tongrui.study.mapper.FavoriteMapper;
import com.tongrui.study.mapper.KnowledgePointMapper;
import com.tongrui.study.mapper.QuestionMapper;
import com.tongrui.study.mapper.QuestionOptionMapper;
import com.tongrui.study.mapper.SubjectMapper;
import com.tongrui.study.mapper.WrongQuestionMapper;
import com.tongrui.study.service.FavoriteService;
import com.tongrui.study.service.WrongQuestionService;
import com.tongrui.study.vo.PracticeQuestionListVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeServiceImplTests {

    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper questionOptionMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;
    @Mock private ChapterMapper chapterMapper;
    @Mock private SubjectMapper subjectMapper;
    @Mock private AnswerRecordMapper answerRecordMapper;
    @Mock private WrongQuestionMapper wrongQuestionMapper;
    @Mock private FavoriteMapper favoriteMapper;
    @Mock private WrongQuestionService wrongQuestionService;
    @Mock private FavoriteService favoriteService;

    @Test
    void knowledgePointPracticeKeepsEnabledHierarchyAndQuestionOrder() {
        KnowledgePoint point = new KnowledgePoint();
        point.setId(8L);
        point.setChapterId(2L);
        point.setName("线性表");
        point.setStatus(1);
        Chapter chapter = new Chapter();
        chapter.setId(2L);
        chapter.setSubjectId(1L);
        chapter.setStatus(1);
        Subject subject = new Subject();
        subject.setId(1L);
        subject.setStatus(1);
        when(knowledgePointMapper.selectById(8L)).thenReturn(point);
        when(chapterMapper.selectById(2L)).thenReturn(chapter);
        when(subjectMapper.selectById(1L)).thenReturn(subject);
        when(questionMapper.selectList(any())).thenReturn(List.of(question(10L), question(11L)));
        when(questionOptionMapper.selectList(any())).thenReturn(List.of(option(10L, "A", "选项A")));

        PracticeQuestionListVO result = service().listQuestions(PracticeSource.KNOWLEDGE_POINT, 8L, 3L);

        assertEquals(PracticeSource.KNOWLEDGE_POINT, result.getSource());
        assertEquals(8L, result.getKnowledgePointId());
        assertEquals("线性表", result.getKnowledgePointName());
        assertEquals(List.of(10L, 11L), result.getQuestions().stream().map(q -> q.getId()).toList());
        assertEquals(1, result.getQuestions().get(0).getOptions().size());
    }

    @Test
    void wrongPracticeUsesOnlyCurrentUsersOrderedCollectionAndBatchLoadsOptions() {
        when(wrongQuestionMapper.selectPracticeQuestions(eq(3L), any()))
                .thenReturn(List.of(question(22L), question(11L)));
        when(questionOptionMapper.selectList(any())).thenReturn(List.of(
                option(22L, "A", "第一题选项"), option(11L, "A", "第二题选项")));

        PracticeQuestionListVO result = service().listQuestions(PracticeSource.WRONG, null, 3L);

        assertEquals(PracticeSource.WRONG, result.getSource());
        assertEquals("错题专项练习", result.getKnowledgePointName());
        assertEquals(List.of(22L, 11L), result.getQuestions().stream().map(q -> q.getId()).toList());
        verify(wrongQuestionMapper).selectPracticeQuestions(eq(3L), any());
        verify(favoriteMapper, never()).selectPracticeQuestions(any(), any());
        verify(questionOptionMapper).selectList(any());
        assertPracticeDtoDoesNotExposeAnswers();
    }

    @Test
    void favoritePracticeUsesCurrentUserAndEmptyCollectionIsSafe() {
        when(favoriteMapper.selectPracticeQuestions(eq(7L), any())).thenReturn(List.of());

        PracticeQuestionListVO result = service().listQuestions(PracticeSource.FAVORITE, null, 7L);

        assertEquals(PracticeSource.FAVORITE, result.getSource());
        assertEquals("收藏专项练习", result.getKnowledgePointName());
        assertEquals(List.of(), result.getQuestions());
        verify(favoriteMapper).selectPracticeQuestions(eq(7L), any());
        verify(questionOptionMapper, never()).selectList(any());
    }

    @Test
    void knowledgePointSourceRequiresKnowledgePointId() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service().listQuestions(PracticeSource.KNOWLEDGE_POINT, null, 3L));

        assertEquals(400, exception.getCode());
        assertEquals("知识点练习必须提供knowledgePointId", exception.getMessage());
    }

    @Test
    void invalidPracticeSourceReturnsControlledBadRequest() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> PracticeSource.from("someones-questions"));

        assertEquals(400, exception.getCode());
        assertEquals("练习来源不合法", exception.getMessage());
    }

    private PracticeServiceImpl service() {
        return new PracticeServiceImpl(questionMapper, questionOptionMapper, knowledgePointMapper,
                chapterMapper, subjectMapper, answerRecordMapper, wrongQuestionMapper, favoriteMapper,
                wrongQuestionService, favoriteService);
    }

    private Question question(Long id) {
        Question question = new Question();
        question.setId(id);
        question.setKnowledgePointId(8L);
        question.setType("SINGLE");
        question.setContent("题目" + id);
        question.setAnswer("A");
        question.setAnalysis("解析" + id);
        question.setDifficulty(1);
        question.setStatus(1);
        return question;
    }

    private QuestionOption option(Long questionId, String key, String content) {
        QuestionOption option = new QuestionOption();
        option.setQuestionId(questionId);
        option.setOptionKey(key);
        option.setContent(content);
        option.setSortOrder(1);
        return option;
    }

    private void assertPracticeDtoDoesNotExposeAnswers() {
        List<String> fields = Arrays.stream(com.tongrui.study.vo.PracticeQuestionVO.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).toList();
        assertFalse(fields.contains("answer"));
        assertFalse(fields.contains("correctAnswer"));
        assertFalse(fields.contains("analysis"));
    }
}
