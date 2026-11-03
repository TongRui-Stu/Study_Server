package com.tongrui.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tongrui.study.dto.QuestionExportFilter;
import com.tongrui.study.entity.Chapter;
import com.tongrui.study.entity.KnowledgePoint;
import com.tongrui.study.entity.Question;
import com.tongrui.study.entity.Subject;
import com.tongrui.study.mapper.ChapterMapper;
import com.tongrui.study.mapper.KnowledgePointMapper;
import com.tongrui.study.mapper.QuestionMapper;
import com.tongrui.study.mapper.QuestionOptionMapper;
import com.tongrui.study.mapper.SubjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceImplExportTests {

    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionOptionMapper optionMapper;
    @Mock
    private KnowledgePointMapper knowledgePointMapper;
    @Mock
    private ChapterMapper chapterMapper;
    @Mock
    private SubjectMapper subjectMapper;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void exportUsesEveryCurrentFilterWithoutPagination() {
        Subject subject = new Subject();
        subject.setId(1L);
        Chapter chapter = new Chapter();
        chapter.setId(2L);
        chapter.setSubjectId(1L);
        KnowledgePoint point = new KnowledgePoint();
        point.setId(3L);
        point.setChapterId(2L);
        when(subjectMapper.selectById(1L)).thenReturn(subject);
        when(chapterMapper.selectById(2L)).thenReturn(chapter);
        when(knowledgePointMapper.selectById(3L)).thenReturn(point);
        when(questionMapper.selectList(any())).thenReturn(List.of());
        QuestionServiceImpl service = new QuestionServiceImpl(questionMapper, optionMapper,
                knowledgePointMapper, chapterMapper, subjectMapper);

        service.listForExport(new QuestionExportFilter(1L, 2L, 3L,
                "single", 2, 1, "  集合  "));

        ArgumentCaptor<LambdaQueryWrapper<Question>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(questionMapper).selectList(captor.capture());
        verify(questionMapper, never()).selectPage(any(Page.class), any());
        String sql = captor.getValue().getCustomSqlSegment().toLowerCase();
        String parameters = captor.getValue().getParamNameValuePairs().values().toString();
        assertTrue(sql.contains("subject_id = 1"));
        assertTrue(sql.contains("chapter_id = 2"));
        assertTrue(sql.contains("knowledge_point_id"));
        assertTrue(sql.contains("type"));
        assertTrue(sql.contains("difficulty"));
        assertTrue(sql.contains("status"));
        assertTrue(sql.contains("content"));
        assertTrue(parameters.contains("SINGLE"));
        assertTrue(parameters.contains("2"));
        assertTrue(parameters.contains("1"));
        assertTrue(parameters.contains("集合"));
    }
}
