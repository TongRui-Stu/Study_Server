package com.tongrui.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.dto.PersonalQuestionFilter;
import com.tongrui.study.entity.Chapter;
import com.tongrui.study.entity.KnowledgePoint;
import com.tongrui.study.entity.Question;
import com.tongrui.study.entity.QuestionOption;
import com.tongrui.study.entity.Subject;
import com.tongrui.study.mapper.ChapterMapper;
import com.tongrui.study.mapper.FavoriteMapper;
import com.tongrui.study.mapper.KnowledgePointMapper;
import com.tongrui.study.mapper.QuestionMapper;
import com.tongrui.study.mapper.QuestionOptionMapper;
import com.tongrui.study.mapper.SubjectMapper;
import com.tongrui.study.service.FavoriteService;
import com.tongrui.study.vo.FavoriteVO;
import com.tongrui.study.vo.PageVO;
import com.tongrui.study.vo.PracticeOptionVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final ChapterMapper chapterMapper;
    private final SubjectMapper subjectMapper;

    public FavoriteServiceImpl(FavoriteMapper favoriteMapper, QuestionMapper questionMapper,
                               QuestionOptionMapper questionOptionMapper,
                               KnowledgePointMapper knowledgePointMapper, ChapterMapper chapterMapper,
                               SubjectMapper subjectMapper) {
        this.favoriteMapper = favoriteMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.chapterMapper = chapterMapper;
        this.subjectMapper = subjectMapper;
    }

    @Override
    public PageVO<FavoriteVO> page(long current, long size, Long userId, Long subjectId,
                                   Long chapterId, Long knowledgePointId, String type, Integer difficulty,
                                   String keyword, String favoriteTimeOrder) {
        PersonalQuestionFilter filter = PersonalQuestionFilter.of(subjectId, chapterId, knowledgePointId,
                type, difficulty, keyword, null);
        filter.setFavoriteTimeOrder(favoriteTimeOrder);
        filter = filter.normalized();
        IPage<FavoriteVO> result = favoriteMapper.selectUserPage(new Page<>(current, size), userId, filter);
        fillOptions(result.getRecords());
        return new PageVO<>(result.getRecords(), result.getTotal(), result.getCurrent(),
                result.getSize(), result.getPages());
    }

    @Override
    public List<FavoriteVO> listForExport(Long userId, PersonalQuestionFilter filter) {
        List<FavoriteVO> records = favoriteMapper.selectUserList(userId, filter.normalized());
        fillOptions(records);
        return records;
    }

    @Override
    public void favorite(Long userId, Long questionId) {
        requirePracticeableQuestion(questionId);
        favoriteMapper.insertIfAbsent(userId, questionId);
    }

    @Override
    public void unfavorite(Long userId, Long questionId) {
        favoriteMapper.deleteByUserAndQuestion(userId, questionId);
    }

    @Override
    public boolean isFavorited(Long userId, Long questionId) {
        return favoriteMapper.countByUserAndQuestion(userId, questionId) > 0;
    }

    private void requirePracticeableQuestion(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null || !Integer.valueOf(1).equals(question.getStatus())) {
            throw new BusinessException(404, "题目不存在或未启用");
        }
        KnowledgePoint knowledgePoint = knowledgePointMapper.selectById(question.getKnowledgePointId());
        if (knowledgePoint == null || !Integer.valueOf(1).equals(knowledgePoint.getStatus())) {
            throw new BusinessException(404, "所属知识点不存在或未启用");
        }
        Chapter chapter = chapterMapper.selectById(knowledgePoint.getChapterId());
        if (chapter == null || !Integer.valueOf(1).equals(chapter.getStatus())) {
            throw new BusinessException(404, "所属章节不存在或未启用");
        }
        Subject subject = subjectMapper.selectById(chapter.getSubjectId());
        if (subject == null || !Integer.valueOf(1).equals(subject.getStatus())) {
            throw new BusinessException(404, "所属科目不存在或未启用");
        }
    }

    private void fillOptions(List<FavoriteVO> records) {
        if (records.isEmpty()) {
            return;
        }
        Set<Long> questionIds = records.stream().map(FavoriteVO::getQuestionId).collect(Collectors.toSet());
        Map<Long, List<PracticeOptionVO>> optionsByQuestion = questionOptionMapper.selectList(
                        new LambdaQueryWrapper<QuestionOption>()
                                .in(QuestionOption::getQuestionId, questionIds)
                                .orderByAsc(QuestionOption::getQuestionId, QuestionOption::getSortOrder,
                                        QuestionOption::getOptionKey))
                .stream().collect(Collectors.groupingBy(QuestionOption::getQuestionId,
                        LinkedHashMap::new,
                        Collectors.mapping(PracticeOptionVO::from, Collectors.toList())));
        records.forEach(record -> record.setOptions(
                optionsByQuestion.getOrDefault(record.getQuestionId(), List.of())));
    }

}
