package com.tongrui.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.dto.KnowledgePointSaveRequest;
import com.tongrui.study.entity.Chapter;
import com.tongrui.study.entity.KnowledgePoint;
import com.tongrui.study.entity.Subject;
import com.tongrui.study.mapper.ChapterMapper;
import com.tongrui.study.mapper.KnowledgePointMapper;
import com.tongrui.study.mapper.SubjectMapper;
import com.tongrui.study.service.KnowledgePointService;
import com.tongrui.study.vo.KnowledgePointVO;
import com.tongrui.study.vo.PageVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class KnowledgePointServiceImpl implements KnowledgePointService {

    private final KnowledgePointMapper knowledgePointMapper;
    private final ChapterMapper chapterMapper;
    private final SubjectMapper subjectMapper;

    public KnowledgePointServiceImpl(KnowledgePointMapper knowledgePointMapper, ChapterMapper chapterMapper,
                                     SubjectMapper subjectMapper) {
        this.knowledgePointMapper = knowledgePointMapper;
        this.chapterMapper = chapterMapper;
        this.subjectMapper = subjectMapper;
    }

    @Override
    public List<KnowledgePointVO> listEnabledByChapter(Long chapterId) {
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null || !Integer.valueOf(1).equals(chapter.getStatus())) {
            throw new BusinessException(404, "章节不存在或未启用");
        }
        Subject subject = subjectMapper.selectById(chapter.getSubjectId());
        if (subject == null || !Integer.valueOf(1).equals(subject.getStatus())) {
            throw new BusinessException(404, "所属科目不存在或未启用");
        }
        return knowledgePointMapper.selectList(new LambdaQueryWrapper<KnowledgePoint>()
                        .eq(KnowledgePoint::getChapterId, chapterId)
                        .eq(KnowledgePoint::getStatus, 1)
                        .orderByAsc(KnowledgePoint::getSortOrder, KnowledgePoint::getId))
                .stream().map(KnowledgePointVO::from).toList();
    }

    @Override
    public PageVO<KnowledgePointVO> page(long current, long size, Long subjectId, Long chapterId, String keyword) {
        if (subjectId != null) {
            Subject subject = subjectMapper.selectById(subjectId);
            if (subject == null) {
                throw new BusinessException(404, "科目不存在");
            }
        }
        if (chapterId != null) {
            Chapter chapter = requireChapter(chapterId);
            if (subjectId != null && !subjectId.equals(chapter.getSubjectId())) {
                throw new BusinessException(400, "章节不属于所选科目");
            }
        }
        LambdaQueryWrapper<KnowledgePoint> query = new LambdaQueryWrapper<KnowledgePoint>()
                .inSql(subjectId != null, KnowledgePoint::getChapterId,
                        "SELECT id FROM chapter WHERE subject_id = " + subjectId)
                .eq(chapterId != null, KnowledgePoint::getChapterId, chapterId)
                .like(StringUtils.hasText(keyword), KnowledgePoint::getName,
                        keyword == null ? null : keyword.trim())
                .orderByAsc(KnowledgePoint::getChapterId, KnowledgePoint::getSortOrder,
                        KnowledgePoint::getId);
        return PageVO.from(knowledgePointMapper.selectPage(new Page<>(current, size), query),
                KnowledgePointVO::from);
    }

    @Override
    @Transactional
    public KnowledgePointVO create(KnowledgePointSaveRequest request) {
        requireChapter(request.getChapterId());
        KnowledgePoint knowledgePoint = new KnowledgePoint();
        apply(knowledgePoint, request);
        ensureNameAvailable(knowledgePoint.getChapterId(), knowledgePoint.getName(), null);
        try {
            knowledgePointMapper.insert(knowledgePoint);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "该章节下知识点名称已存在");
        }
        return KnowledgePointVO.from(knowledgePoint);
    }

    @Override
    @Transactional
    public KnowledgePointVO update(Long id, KnowledgePointSaveRequest request) {
        KnowledgePoint knowledgePoint = requireKnowledgePoint(id);
        requireChapter(request.getChapterId());
        apply(knowledgePoint, request);
        ensureNameAvailable(knowledgePoint.getChapterId(), knowledgePoint.getName(), id);
        try {
            knowledgePointMapper.updateById(knowledgePoint);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "该章节下知识点名称已存在");
        }
        return KnowledgePointVO.from(knowledgePoint);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        KnowledgePoint knowledgePoint = requireKnowledgePoint(id);
        knowledgePoint.setStatus(status);
        knowledgePointMapper.updateById(knowledgePoint);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireKnowledgePoint(id);
        if (knowledgePointMapper.countQuestions(id) > 0) {
            throw new BusinessException(409, "该知识点下存在题目，无法删除");
        }
        knowledgePointMapper.deleteById(id);
    }

    private KnowledgePoint requireKnowledgePoint(Long id) {
        KnowledgePoint knowledgePoint = knowledgePointMapper.selectById(id);
        if (knowledgePoint == null) {
            throw new BusinessException(404, "知识点不存在");
        }
        return knowledgePoint;
    }

    private Chapter requireChapter(Long id) {
        Chapter chapter = chapterMapper.selectById(id);
        if (chapter == null) {
            throw new BusinessException(404, "章节不存在");
        }
        return chapter;
    }

    private void ensureNameAvailable(Long chapterId, String name, Long excludedId) {
        LambdaQueryWrapper<KnowledgePoint> query = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getChapterId, chapterId).eq(KnowledgePoint::getName, name);
        query.ne(excludedId != null, KnowledgePoint::getId, excludedId);
        if (knowledgePointMapper.selectCount(query) > 0) {
            throw new BusinessException(409, "该章节下知识点名称已存在");
        }
    }

    private void apply(KnowledgePoint knowledgePoint, KnowledgePointSaveRequest request) {
        knowledgePoint.setChapterId(request.getChapterId());
        knowledgePoint.setName(request.getName().trim());
        knowledgePoint.setDescription(StringUtils.hasText(request.getDescription())
                ? request.getDescription().trim() : null);
        knowledgePoint.setSortOrder(request.getSortOrder());
        knowledgePoint.setStatus(request.getStatus());
    }
}
