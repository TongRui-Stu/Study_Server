package com.tongrui.study.service;

import com.tongrui.study.dto.PracticeSubmitRequest;
import com.tongrui.study.dto.PersonalQuestionFilter;
import com.tongrui.study.enums.PracticeSource;
import com.tongrui.study.vo.PracticeQuestionListVO;
import com.tongrui.study.vo.PracticeResultVO;

public interface PracticeService {

    default PracticeQuestionListVO listQuestions(PracticeSource source, Long knowledgePointId, Long userId) {
        return listQuestions(source, knowledgePointId, userId, new PersonalQuestionFilter());
    }

    PracticeQuestionListVO listQuestions(PracticeSource source, Long knowledgePointId, Long userId,
                                        PersonalQuestionFilter filter);

    PracticeResultVO submit(PracticeSubmitRequest request, Long userId);
}
