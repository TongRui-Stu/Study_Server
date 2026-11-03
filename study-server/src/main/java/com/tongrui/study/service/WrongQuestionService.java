package com.tongrui.study.service;

import com.tongrui.study.dto.PersonalQuestionFilter;
import com.tongrui.study.vo.PageVO;
import com.tongrui.study.vo.WrongQuestionVO;

import java.util.List;

public interface WrongQuestionService {

    PageVO<WrongQuestionVO> page(long current, long size, Long userId, Long subjectId,
                                 Long chapterId, Long knowledgePointId, String type, Integer difficulty,
                                 String keyword, String wrongCountOrder);

    default List<WrongQuestionVO> listForExport(Long userId) {
        return listForExport(userId, new PersonalQuestionFilter());
    }

    List<WrongQuestionVO> listForExport(Long userId, PersonalQuestionFilter filter);

    void recordWrong(Long userId, Long questionId);

    void remove(Long userId, Long questionId);
}
