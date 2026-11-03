package com.tongrui.study.service;

import com.tongrui.study.dto.ChapterSaveRequest;
import com.tongrui.study.vo.ChapterVO;
import com.tongrui.study.vo.PageVO;

import java.util.List;

public interface ChapterService {
    List<ChapterVO> listEnabledBySubject(Long subjectId);
    PageVO<ChapterVO> page(long current, long size, Long subjectId, String keyword);
    ChapterVO create(ChapterSaveRequest request);
    ChapterVO update(Long id, ChapterSaveRequest request);
    void updateStatus(Long id, Integer status);
    void delete(Long id);
}
