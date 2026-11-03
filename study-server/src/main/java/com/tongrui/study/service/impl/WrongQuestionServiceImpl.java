package com.tongrui.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.dto.PersonalQuestionFilter;
import com.tongrui.study.entity.QuestionOption;
import com.tongrui.study.mapper.QuestionOptionMapper;
import com.tongrui.study.mapper.WrongQuestionMapper;
import com.tongrui.study.service.WrongQuestionService;
import com.tongrui.study.vo.PageVO;
import com.tongrui.study.vo.PracticeOptionVO;
import com.tongrui.study.vo.WrongQuestionVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WrongQuestionServiceImpl implements WrongQuestionService {

    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionOptionMapper questionOptionMapper;

    public WrongQuestionServiceImpl(WrongQuestionMapper wrongQuestionMapper,
                                    QuestionOptionMapper questionOptionMapper) {
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.questionOptionMapper = questionOptionMapper;
    }

    @Override
    public PageVO<WrongQuestionVO> page(long current, long size, Long userId, Long subjectId,
                                        Long chapterId, Long knowledgePointId, String type, Integer difficulty,
                                        String keyword, String wrongCountOrder) {
        PersonalQuestionFilter filter = PersonalQuestionFilter.of(subjectId, chapterId, knowledgePointId,
                type, difficulty, keyword, wrongCountOrder);
        IPage<WrongQuestionVO> result = wrongQuestionMapper.selectUserPage(new Page<>(current, size), userId, filter);
        fillOptions(result.getRecords());
        return new PageVO<>(result.getRecords(), result.getTotal(), result.getCurrent(),
                result.getSize(), result.getPages());
    }

    @Override
    public List<WrongQuestionVO> listForExport(Long userId, PersonalQuestionFilter filter) {
        List<WrongQuestionVO> records = wrongQuestionMapper.selectUserList(userId, filter.normalized());
        fillOptions(records);
        return records;
    }

    @Override
    public void recordWrong(Long userId, Long questionId) {
        wrongQuestionMapper.upsertWrong(userId, questionId);
    }

    @Override
    public void remove(Long userId, Long questionId) {
        wrongQuestionMapper.deleteByUserAndQuestion(userId, questionId);
    }

    private void fillOptions(List<WrongQuestionVO> records) {
        if (records.isEmpty()) {
            return;
        }
        Set<Long> questionIds = records.stream().map(WrongQuestionVO::getQuestionId).collect(Collectors.toSet());
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
