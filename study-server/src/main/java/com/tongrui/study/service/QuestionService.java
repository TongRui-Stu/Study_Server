package com.tongrui.study.service;

import com.tongrui.study.dto.QuestionSaveRequest;
import com.tongrui.study.dto.QuestionExportFilter;
import com.tongrui.study.vo.PageVO;
import com.tongrui.study.vo.QuestionVO;

public interface QuestionService {
    PageVO<QuestionVO> page(long page, long size, Long subjectId, Long chapterId,
                            Long knowledgePointId, String type, Integer difficulty,
                            Integer status, String keyword);
    java.util.List<QuestionVO> listForExport(QuestionExportFilter filter);
    QuestionVO getById(Long id);
    QuestionVO create(QuestionSaveRequest request, Long createdBy);
    QuestionVO update(Long id, QuestionSaveRequest request);
    void updateStatus(Long id, Integer status);
    void delete(Long id);
}
