package com.tongrui.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.dto.ChapterSaveRequest;
import com.tongrui.study.entity.Chapter;
import com.tongrui.study.entity.KnowledgePoint;
import com.tongrui.study.entity.Subject;
import com.tongrui.study.mapper.ChapterMapper;
import com.tongrui.study.mapper.KnowledgePointMapper;
import com.tongrui.study.mapper.SubjectMapper;
import com.tongrui.study.service.ChapterService;
import com.tongrui.study.vo.ChapterVO;
import com.tongrui.study.vo.PageVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ChapterServiceImpl implements ChapterService {

    private final ChapterMapper chapterMapper;
    private final SubjectMapper subjectMapper;
    private final KnowledgePointMapper knowledgePointMapper;

    public ChapterServiceImpl(ChapterMapper chapterMapper, SubjectMapper subjectMapper,
                              KnowledgePointMapper knowledgePointMapper) {
        this.chapterMapper = chapterMapper;
        this.subjectMapper = subjectMapper;
        this.knowledgePointMapper = knowledgePointMapper;
    }

    @Override
    public List<ChapterVO> listEnabledBySubject(Long subjectId) {
        Subject subject = subjectMapper.selectById(subjectId);
        if (subject == null || !Integer.valueOf(1).equals(subject.getStatus())) {
            throw new BusinessException(404, "科目不存在或未启用");
        }
        return chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getSubjectId, subjectId)
                        .eq(Chapter::getStatus, 1)
                        .orderByAsc(Chapter::getSortOrder, Chapter::getId))
                .stream().map(ChapterVO::from).toList();
    }

    @Override
    public PageVO<ChapterVO> page(long current, long size, Long subjectId, String keyword) {
        if (subjectId != null) {
            requireSubject(subjectId);
        }
        LambdaQueryWrapper<Chapter> query = new LambdaQueryWrapper<Chapter>()
                .eq(subjectId != null, Chapter::getSubjectId, subjectId)
                .like(StringUtils.hasText(keyword), Chapter::getName, keyword == null ? null : keyword.trim())
                .orderByAsc(Chapter::getSubjectId, Chapter::getSortOrder, Chapter::getId);
        return PageVO.from(chapterMapper.selectPage(new Page<>(current, size), query), ChapterVO::from);
    }

    @Override
    @Transactional
    public ChapterVO create(ChapterSaveRequest request) {
        requireSubject(request.getSubjectId());
        Chapter chapter = new Chapter();
        apply(chapter, request);
        ensureNameAvailable(chapter.getSubjectId(), chapter.getName(), null);
        try {
            chapterMapper.insert(chapter);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "该科目下章节名称已存在");
        }
        return ChapterVO.from(chapter);
    }

    @Override
    @Transactional
    public ChapterVO update(Long id, ChapterSaveRequest request) {
        Chapter chapter = requireChapter(id);
        requireSubject(request.getSubjectId());
        apply(chapter, request);
        ensureNameAvailable(chapter.getSubjectId(), chapter.getName(), id);
        try {
            chapterMapper.updateById(chapter);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "该科目下章节名称已存在");
        }
        return ChapterVO.from(chapter);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Chapter chapter = requireChapter(id);
        chapter.setStatus(status);
        chapterMapper.updateById(chapter);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireChapter(id);
        if (knowledgePointMapper.selectCount(new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getChapterId, id)) > 0) {
            throw new BusinessException(409, "该章节下存在知识点，无法删除");
        }
        chapterMapper.deleteById(id);
    }

    private Chapter requireChapter(Long id) {
        Chapter chapter = chapterMapper.selectById(id);
        if (chapter == null) {
            throw new BusinessException(404, "章节不存在");
        }
        return chapter;
    }

    private Subject requireSubject(Long id) {
        Subject subject = subjectMapper.selectById(id);
        if (subject == null) {
            throw new BusinessException(404, "科目不存在");
        }
        return subject;
    }

    private void ensureNameAvailable(Long subjectId, String name, Long excludedId) {
        LambdaQueryWrapper<Chapter> query = new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getSubjectId, subjectId).eq(Chapter::getName, name);
        query.ne(excludedId != null, Chapter::getId, excludedId);
        if (chapterMapper.selectCount(query) > 0) {
            throw new BusinessException(409, "该科目下章节名称已存在");
        }
    }

    private void apply(Chapter chapter, ChapterSaveRequest request) {
        chapter.setSubjectId(request.getSubjectId());
        chapter.setName(request.getName().trim());
        chapter.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        chapter.setSortOrder(request.getSortOrder());
        chapter.setStatus(request.getStatus());
    }
}
