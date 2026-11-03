package com.tongrui.study.service;

import com.tongrui.study.dto.QuestionSaveRequest;
import com.tongrui.study.entity.Question;
import com.tongrui.study.entity.QuestionOption;
import com.tongrui.study.mapper.QuestionMapper;
import com.tongrui.study.mapper.QuestionOptionMapper;
import com.tongrui.study.util.QuestionValidationSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class QuestionImportPersistence {

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;

    public QuestionImportPersistence(QuestionMapper questionMapper, QuestionOptionMapper questionOptionMapper) {
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
    }

    public int persist(QuestionSaveRequest request,
                       QuestionValidationSupport.PreparedQuestion prepared,
                       Long createdBy) {
        Question question = new Question();
        question.setKnowledgePointId(request.getKnowledgePointId());
        question.setType(prepared.type());
        question.setContent(request.getContent().trim());
        question.setAnswer(prepared.answer());
        question.setAnalysis(StringUtils.hasText(request.getAnalysis()) ? request.getAnalysis().trim() : null);
        question.setDifficulty(request.getDifficulty());
        question.setStatus(request.getStatus());
        question.setCreatedBy(createdBy);
        questionMapper.insert(question);

        for (QuestionValidationSupport.NormalizedOption source : prepared.options()) {
            QuestionOption option = new QuestionOption();
            option.setQuestionId(question.getId());
            option.setOptionKey(source.optionKey());
            option.setContent(source.content());
            option.setSortOrder(source.sortOrder());
            questionOptionMapper.insert(option);
        }
        return prepared.options().size();
    }
}
