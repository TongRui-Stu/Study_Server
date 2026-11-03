package com.tongrui.study.service;

import com.tongrui.study.dto.SubjectSaveRequest;
import com.tongrui.study.vo.PageVO;
import com.tongrui.study.vo.SubjectVO;

import java.util.List;

public interface SubjectService {
    List<SubjectVO> listEnabled();
    PageVO<SubjectVO> page(long current, long size, String keyword);
    SubjectVO create(SubjectSaveRequest request);
    SubjectVO update(Long id, SubjectSaveRequest request);
    void updateStatus(Long id, Integer status);
    void delete(Long id);
}
