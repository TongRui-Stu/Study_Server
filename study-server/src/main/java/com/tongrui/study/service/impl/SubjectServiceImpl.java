package com.tongrui.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.dto.SubjectSaveRequest;
import com.tongrui.study.entity.Chapter;
import com.tongrui.study.entity.Subject;
import com.tongrui.study.mapper.ChapterMapper;
import com.tongrui.study.mapper.SubjectMapper;
import com.tongrui.study.service.SubjectService;
import com.tongrui.study.vo.PageVO;
import com.tongrui.study.vo.SubjectVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class SubjectServiceImpl implements SubjectService {

    private final SubjectMapper subjectMapper;
    private final ChapterMapper chapterMapper;

    public SubjectServiceImpl(SubjectMapper subjectMapper, ChapterMapper chapterMapper) {
        this.subjectMapper = subjectMapper;
        this.chapterMapper = chapterMapper;
    }

    @Override
    public List<SubjectVO> listEnabled() {
        return subjectMapper.selectList(new LambdaQueryWrapper<Subject>()
                        .eq(Subject::getStatus, 1)
                        .orderByAsc(Subject::getSortOrder, Subject::getId))
                .stream().map(SubjectVO::from).toList();
    }

    @Override
    public PageVO<SubjectVO> page(long current, long size, String keyword) {
        LambdaQueryWrapper<Subject> query = new LambdaQueryWrapper<Subject>()
                .like(StringUtils.hasText(keyword), Subject::getName, keyword == null ? null : keyword.trim())
                .orderByAsc(Subject::getSortOrder, Subject::getId);
        return PageVO.from(subjectMapper.selectPage(new Page<>(current, size), query), SubjectVO::from);
    }

    @Override
    @Transactional
    public SubjectVO create(SubjectSaveRequest request) {
        Subject subject = new Subject();
        apply(subject, request);
        ensureNameAvailable(subject.getName(), null);
        try {
            subjectMapper.insert(subject);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "科目名称已存在");
        }
        return SubjectVO.from(subject);
    }

    @Override
    @Transactional
    public SubjectVO update(Long id, SubjectSaveRequest request) {
        Subject subject = requireSubject(id);
        apply(subject, request);
        ensureNameAvailable(subject.getName(), id);
        try {
            subjectMapper.updateById(subject);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "科目名称已存在");
        }
        return SubjectVO.from(subject);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Subject subject = requireSubject(id);
        subject.setStatus(status);
        subjectMapper.updateById(subject);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireSubject(id);
        if (chapterMapper.selectCount(new LambdaQueryWrapper<Chapter>().eq(Chapter::getSubjectId, id)) > 0) {
            throw new BusinessException(409, "该科目下存在章节，无法删除");
        }
        subjectMapper.deleteById(id);
    }

    private Subject requireSubject(Long id) {
        Subject subject = subjectMapper.selectById(id);
        if (subject == null) {
            throw new BusinessException(404, "科目不存在");
        }
        return subject;
    }

    private void ensureNameAvailable(String name, Long excludedId) {
        LambdaQueryWrapper<Subject> query = new LambdaQueryWrapper<Subject>().eq(Subject::getName, name);
        query.ne(excludedId != null, Subject::getId, excludedId);
        if (subjectMapper.selectCount(query) > 0) {
            throw new BusinessException(409, "科目名称已存在");
        }
    }

    private void apply(Subject subject, SubjectSaveRequest request) {
        subject.setName(request.getName().trim());
        subject.setDescription(normalizeDescription(request.getDescription()));
        subject.setSortOrder(request.getSortOrder());
        subject.setStatus(request.getStatus());
    }

    private String normalizeDescription(String description) {
        return StringUtils.hasText(description) ? description.trim() : null;
    }
}
