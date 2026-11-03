package com.tongrui.study.service;

import com.tongrui.study.dto.PersonalQuestionFilter;
import com.tongrui.study.vo.FavoriteVO;
import com.tongrui.study.vo.PageVO;

import java.util.List;

public interface FavoriteService {

    default PageVO<FavoriteVO> page(long current, long size, Long userId, Long subjectId,
                            Long chapterId, Long knowledgePointId, String type, Integer difficulty,
                            String keyword) {
        return page(current, size, userId, subjectId, chapterId, knowledgePointId, type,
                difficulty, keyword, null);
    }

    PageVO<FavoriteVO> page(long current, long size, Long userId, Long subjectId,
                            Long chapterId, Long knowledgePointId, String type, Integer difficulty,
                            String keyword, String favoriteTimeOrder);

    default List<FavoriteVO> listForExport(Long userId) {
        return listForExport(userId, new PersonalQuestionFilter());
    }

    List<FavoriteVO> listForExport(Long userId, PersonalQuestionFilter filter);

    void favorite(Long userId, Long questionId);

    void unfavorite(Long userId, Long questionId);

    boolean isFavorited(Long userId, Long questionId);
}
