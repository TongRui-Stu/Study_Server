package com.tongrui.study.service;

import com.tongrui.study.dto.KnowledgePointSaveRequest;
import com.tongrui.study.vo.KnowledgePointVO;
import com.tongrui.study.vo.PageVO;

import java.util.List;

public interface KnowledgePointService {
    List<KnowledgePointVO> listEnabledByChapter(Long chapterId);
    PageVO<KnowledgePointVO> page(long current, long size, Long subjectId, Long chapterId, String keyword);
    KnowledgePointVO create(KnowledgePointSaveRequest request);
    KnowledgePointVO update(Long id, KnowledgePointSaveRequest request);
    void updateStatus(Long id, Integer status);
    void delete(Long id);
}
