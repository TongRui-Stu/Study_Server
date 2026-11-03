package com.tongrui.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.dto.QuestionOptionRequest;
import com.tongrui.study.dto.QuestionExportFilter;
import com.tongrui.study.dto.QuestionSaveRequest;
import com.tongrui.study.entity.Chapter;
import com.tongrui.study.entity.KnowledgePoint;
import com.tongrui.study.entity.Question;
import com.tongrui.study.entity.QuestionOption;
import com.tongrui.study.entity.Subject;
import com.tongrui.study.mapper.ChapterMapper;
import com.tongrui.study.mapper.KnowledgePointMapper;
import com.tongrui.study.mapper.QuestionMapper;
import com.tongrui.study.mapper.QuestionOptionMapper;
import com.tongrui.study.mapper.SubjectMapper;
import com.tongrui.study.service.QuestionService;
import com.tongrui.study.util.QuestionValidationSupport;
import com.tongrui.study.vo.PageVO;
import com.tongrui.study.vo.QuestionOptionVO;
import com.tongrui.study.vo.QuestionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuestionServiceImpl implements QuestionService {

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final ChapterMapper chapterMapper;
    private final SubjectMapper subjectMapper;

    public QuestionServiceImpl(QuestionMapper questionMapper, QuestionOptionMapper questionOptionMapper,
                               KnowledgePointMapper knowledgePointMapper, ChapterMapper chapterMapper,
                               SubjectMapper subjectMapper) {
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.chapterMapper = chapterMapper;
        this.subjectMapper = subjectMapper;
    }

    @Override
    public PageVO<QuestionVO> page(long current, long size, Long subjectId, Long chapterId,
                                   Long knowledgePointId, String type, Integer difficulty,
                                   Integer status, String keyword) {
        LambdaQueryWrapper<Question> query = buildQuery(subjectId, chapterId, knowledgePointId,
                type, difficulty, status, keyword);
        Page<Question> result = questionMapper.selectPage(new Page<>(current, size), query);
        List<QuestionVO> records = enrich(result.getRecords());
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    @Override
    public List<QuestionVO> listForExport(QuestionExportFilter filter) {
        LambdaQueryWrapper<Question> query = buildQuery(filter.subjectId(), filter.chapterId(),
                filter.knowledgePointId(), filter.type(), filter.difficulty(), filter.status(), filter.keyword());
        return enrich(questionMapper.selectList(query));
    }

    @Override
    public QuestionVO getById(Long id) {
        Question question = requireQuestion(id);
        return enrich(List.of(question)).get(0);
    }

    @Override
    @Transactional
    public QuestionVO create(QuestionSaveRequest request, Long createdBy) {
        requireHierarchy(request.getKnowledgePointId());
        PreparedQuestion prepared = prepare(request);

        Question question = new Question();
        apply(question, request, prepared);
        question.setCreatedBy(createdBy);
        questionMapper.insert(question);
        replaceOptions(question.getId(), prepared.options());
        return getById(question.getId());
    }

    @Override
    @Transactional
    public QuestionVO update(Long id, QuestionSaveRequest request) {
        Question question = requireQuestion(id);
        requireHierarchy(request.getKnowledgePointId());
        PreparedQuestion prepared = prepare(request);

        apply(question, request, prepared);
        questionMapper.updateById(question);
        replaceOptions(question.getId(), prepared.options());
        return getById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Question question = requireQuestion(id);
        question.setStatus(status);
        questionMapper.updateById(question);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireQuestion(id);
        if (questionMapper.countLearningRecords(id) > 0) {
            throw new BusinessException(409, "该题目已有学习记录，无法删除，可选择禁用");
        }
        questionOptionMapper.delete(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, id));
        questionMapper.deleteById(id);
    }

    private PreparedQuestion prepare(QuestionSaveRequest request) {
        QuestionValidationSupport.PreparedQuestion prepared = QuestionValidationSupport.prepare(request);
        List<NormalizedOption> options = prepared.options().stream()
                .map(option -> new NormalizedOption(option.optionKey(), option.content(), option.sortOrder()))
                .toList();
        return new PreparedQuestion(prepared.type(), prepared.answer(), options);
    }

    private String normalizeType(String type) {
        return QuestionValidationSupport.normalizeType(type);
    }

    private void apply(Question question, QuestionSaveRequest request, PreparedQuestion prepared) {
        question.setKnowledgePointId(request.getKnowledgePointId());
        question.setType(prepared.type());
        question.setContent(request.getContent().trim());
        question.setAnswer(prepared.answer());
        question.setAnalysis(StringUtils.hasText(request.getAnalysis()) ? request.getAnalysis().trim() : null);
        question.setDifficulty(request.getDifficulty());
        question.setStatus(request.getStatus());
    }

    private void replaceOptions(Long questionId, List<NormalizedOption> options) {
        questionOptionMapper.delete(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, questionId));
        for (NormalizedOption source : options) {
            QuestionOption option = new QuestionOption();
            option.setQuestionId(questionId);
            option.setOptionKey(source.optionKey());
            option.setContent(source.content());
            option.setSortOrder(source.sortOrder());
            questionOptionMapper.insert(option);
        }
    }

    private Question requireQuestion(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BusinessException(404, "题目不存在");
        }
        return question;
    }

    private Hierarchy requireHierarchy(Long knowledgePointId) {
        KnowledgePoint knowledgePoint = knowledgePointMapper.selectById(knowledgePointId);
        if (knowledgePoint == null) {
            throw new BusinessException(404, "知识点不存在");
        }
        Chapter chapter = chapterMapper.selectById(knowledgePoint.getChapterId());
        if (chapter == null) {
            throw new BusinessException(404, "所属章节不存在");
        }
        Subject subject = subjectMapper.selectById(chapter.getSubjectId());
        if (subject == null) {
            throw new BusinessException(404, "所属科目不存在");
        }
        return new Hierarchy(subject, chapter, knowledgePoint);
    }

    private void validateHierarchyFilter(Long subjectId, Long chapterId, Long knowledgePointId) {
        Subject subject = null;
        Chapter chapter = null;
        KnowledgePoint knowledgePoint = null;
        if (subjectId != null) {
            subject = subjectMapper.selectById(subjectId);
            if (subject == null) {
                throw new BusinessException(404, "科目不存在");
            }
        }
        if (chapterId != null) {
            chapter = chapterMapper.selectById(chapterId);
            if (chapter == null) {
                throw new BusinessException(404, "章节不存在");
            }
            if (subject != null && !subject.getId().equals(chapter.getSubjectId())) {
                throw new BusinessException("章节不属于所选科目");
            }
        }
        if (knowledgePointId != null) {
            knowledgePoint = knowledgePointMapper.selectById(knowledgePointId);
            if (knowledgePoint == null) {
                throw new BusinessException(404, "知识点不存在");
            }
            if (chapter != null && !chapter.getId().equals(knowledgePoint.getChapterId())) {
                throw new BusinessException("知识点不属于所选章节");
            }
            if (subject != null && chapter == null) {
                Chapter parent = chapterMapper.selectById(knowledgePoint.getChapterId());
                if (parent == null || !subject.getId().equals(parent.getSubjectId())) {
                    throw new BusinessException("知识点不属于所选科目");
                }
            }
        }
    }

    private LambdaQueryWrapper<Question> buildQuery(Long subjectId, Long chapterId,
                                                     Long knowledgePointId, String type,
                                                     Integer difficulty, Integer status,
                                                     String keyword) {
        validateHierarchyFilter(subjectId, chapterId, knowledgePointId);
        String normalizedType = StringUtils.hasText(type) ? normalizeType(type) : null;
        return new LambdaQueryWrapper<Question>()
                .inSql(subjectId != null, Question::getKnowledgePointId,
                        "SELECT kp.id FROM knowledge_point kp JOIN chapter c ON kp.chapter_id = c.id "
                                + "WHERE c.subject_id = " + subjectId)
                .inSql(chapterId != null, Question::getKnowledgePointId,
                        "SELECT id FROM knowledge_point WHERE chapter_id = " + chapterId)
                .eq(knowledgePointId != null, Question::getKnowledgePointId, knowledgePointId)
                .eq(normalizedType != null, Question::getType, normalizedType)
                .eq(difficulty != null, Question::getDifficulty, difficulty)
                .eq(status != null, Question::getStatus, status)
                .like(StringUtils.hasText(keyword), Question::getContent,
                        keyword == null ? null : keyword.trim())
                .orderByDesc(Question::getCreatedAt, Question::getId);
    }

    private List<QuestionVO> enrich(List<Question> questions) {
        if (questions.isEmpty()) {
            return List.of();
        }
        Set<Long> knowledgePointIds = questions.stream().map(Question::getKnowledgePointId)
                .collect(Collectors.toSet());
        Map<Long, KnowledgePoint> knowledgePoints = knowledgePointMapper.selectBatchIds(knowledgePointIds).stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, Function.identity()));
        Set<Long> chapterIds = knowledgePoints.values().stream().map(KnowledgePoint::getChapterId)
                .collect(Collectors.toSet());
        Map<Long, Chapter> chapters = chapterIds.isEmpty() ? Collections.emptyMap()
                : chapterMapper.selectBatchIds(chapterIds).stream()
                .collect(Collectors.toMap(Chapter::getId, Function.identity()));
        Set<Long> subjectIds = chapters.values().stream().map(Chapter::getSubjectId)
                .collect(Collectors.toSet());
        Map<Long, Subject> subjects = subjectIds.isEmpty() ? Collections.emptyMap()
                : subjectMapper.selectBatchIds(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, Function.identity()));

        Set<Long> questionIds = questions.stream().map(Question::getId).collect(Collectors.toSet());
        Map<Long, List<QuestionOptionVO>> options = questionOptionMapper.selectList(
                        new LambdaQueryWrapper<QuestionOption>()
                                .in(QuestionOption::getQuestionId, questionIds)
                                .orderByAsc(QuestionOption::getQuestionId, QuestionOption::getSortOrder,
                                        QuestionOption::getOptionKey))
                .stream().collect(Collectors.groupingBy(QuestionOption::getQuestionId,
                        LinkedHashMap::new,
                        Collectors.mapping(QuestionOptionVO::from, Collectors.toList())));

        return questions.stream().map(question -> {
            KnowledgePoint knowledgePoint = knowledgePoints.get(question.getKnowledgePointId());
            Chapter chapter = knowledgePoint == null ? null : chapters.get(knowledgePoint.getChapterId());
            Subject subject = chapter == null ? null : subjects.get(chapter.getSubjectId());
            return QuestionVO.builder()
                    .id(question.getId())
                    .subjectId(subject == null ? null : subject.getId())
                    .subjectName(subject == null ? null : subject.getName())
                    .chapterId(chapter == null ? null : chapter.getId())
                    .chapterName(chapter == null ? null : chapter.getName())
                    .knowledgePointId(question.getKnowledgePointId())
                    .knowledgePointName(knowledgePoint == null ? null : knowledgePoint.getName())
                    .type(question.getType())
                    .content(question.getContent())
                    .answer(question.getAnswer())
                    .analysis(question.getAnalysis())
                    .difficulty(question.getDifficulty())
                    .status(question.getStatus())
                    .options(options.getOrDefault(question.getId(), List.of()))
                    .createdBy(question.getCreatedBy())
                    .createdAt(question.getCreatedAt())
                    .updatedAt(question.getUpdatedAt())
                    .build();
        }).toList();
    }

    private record NormalizedOption(String optionKey, String content, Integer sortOrder) {
    }

    private record PreparedQuestion(String type, String answer, List<NormalizedOption> options) {
    }

    private record Hierarchy(Subject subject, Chapter chapter, KnowledgePoint knowledgePoint) {
    }
}
