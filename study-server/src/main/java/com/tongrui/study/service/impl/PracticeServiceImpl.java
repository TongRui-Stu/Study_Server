package com.tongrui.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.dto.PracticeSubmitRequest;
import com.tongrui.study.dto.PersonalQuestionFilter;
import com.tongrui.study.enums.PracticeSource;
import com.tongrui.study.entity.AnswerRecord;
import com.tongrui.study.entity.Chapter;
import com.tongrui.study.entity.KnowledgePoint;
import com.tongrui.study.entity.Question;
import com.tongrui.study.entity.QuestionOption;
import com.tongrui.study.entity.Subject;
import com.tongrui.study.mapper.AnswerRecordMapper;
import com.tongrui.study.mapper.ChapterMapper;
import com.tongrui.study.mapper.KnowledgePointMapper;
import com.tongrui.study.mapper.QuestionMapper;
import com.tongrui.study.mapper.QuestionOptionMapper;
import com.tongrui.study.mapper.SubjectMapper;
import com.tongrui.study.mapper.FavoriteMapper;
import com.tongrui.study.mapper.WrongQuestionMapper;
import com.tongrui.study.service.FavoriteService;
import com.tongrui.study.service.PracticeService;
import com.tongrui.study.service.WrongQuestionService;
import com.tongrui.study.vo.PracticeOptionVO;
import com.tongrui.study.vo.PracticeQuestionListVO;
import com.tongrui.study.vo.PracticeQuestionVO;
import com.tongrui.study.vo.PracticeResultVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class PracticeServiceImpl implements PracticeService {

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final ChapterMapper chapterMapper;
    private final SubjectMapper subjectMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final FavoriteMapper favoriteMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final WrongQuestionService wrongQuestionService;
    private final FavoriteService favoriteService;

    public PracticeServiceImpl(QuestionMapper questionMapper, QuestionOptionMapper questionOptionMapper,
                               KnowledgePointMapper knowledgePointMapper, ChapterMapper chapterMapper,
                               SubjectMapper subjectMapper, AnswerRecordMapper answerRecordMapper,
                               WrongQuestionMapper wrongQuestionMapper, FavoriteMapper favoriteMapper,
                               WrongQuestionService wrongQuestionService, FavoriteService favoriteService) {
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.chapterMapper = chapterMapper;
        this.subjectMapper = subjectMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.favoriteMapper = favoriteMapper;
        this.answerRecordMapper = answerRecordMapper;
        this.wrongQuestionService = wrongQuestionService;
        this.favoriteService = favoriteService;
    }

    @Override
    public PracticeQuestionListVO listQuestions(PracticeSource source, Long knowledgePointId, Long userId,
                                                 PersonalQuestionFilter filter) {
        PersonalQuestionFilter normalizedFilter = filter.normalized();
        if (source == PracticeSource.KNOWLEDGE_POINT) {
            if (knowledgePointId == null) {
                throw new BusinessException(400, "知识点练习必须提供knowledgePointId");
            }
            Hierarchy hierarchy = requireEnabledHierarchy(knowledgePointId);
            List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                    .eq(Question::getKnowledgePointId, knowledgePointId)
                    .eq(Question::getStatus, 1)
                    .orderByAsc(Question::getId));
            if (questions.isEmpty()) {
                throw new BusinessException(404, "该知识点暂无可用题目");
            }
            return toQuestionList(source, knowledgePointId, hierarchy.knowledgePoint().getName(), questions);
        }

        List<Question> questions = source == PracticeSource.WRONG
                ? wrongQuestionMapper.selectPracticeQuestions(userId, normalizedFilter)
                : favoriteMapper.selectPracticeQuestions(userId, normalizedFilter);
        String title = source == PracticeSource.WRONG ? "错题专项练习" : "收藏专项练习";
        return toQuestionList(source, null, title, questions);
    }

    private PracticeQuestionListVO toQuestionList(PracticeSource source, Long knowledgePointId,
                                                   String title, List<Question> questions) {
        if (questions.isEmpty()) {
            return new PracticeQuestionListVO(source, knowledgePointId, title, List.of());
        }
        Set<Long> questionIds = questions.stream().map(Question::getId).collect(Collectors.toSet());
        Map<Long, List<PracticeOptionVO>> optionsByQuestion = questionOptionMapper.selectList(
                        new LambdaQueryWrapper<QuestionOption>()
                                .in(QuestionOption::getQuestionId, questionIds)
                                .orderByAsc(QuestionOption::getQuestionId, QuestionOption::getSortOrder,
                                        QuestionOption::getOptionKey))
                .stream().collect(Collectors.groupingBy(QuestionOption::getQuestionId,
                        LinkedHashMap::new,
                        Collectors.mapping(PracticeOptionVO::from, Collectors.toList())));

        List<PracticeQuestionVO> questionVOs = questions.stream()
                .map(question -> new PracticeQuestionVO(
                        question.getId(),
                        question.getType(),
                        question.getContent(),
                        question.getDifficulty(),
                        optionsByQuestion.getOrDefault(question.getId(), List.of())))
                .toList();
        return new PracticeQuestionListVO(source, knowledgePointId, title, questionVOs);
    }

    @Override
    @Transactional
    public PracticeResultVO submit(PracticeSubmitRequest request, Long userId) {
        Question question = questionMapper.selectById(request.getQuestionId());
        if (question == null) {
            throw new BusinessException(404, "题目不存在");
        }
        if (!Integer.valueOf(1).equals(question.getStatus())) {
            throw new BusinessException("该题目当前不可作答");
        }
        requireEnabledHierarchy(question.getKnowledgePointId());

        List<QuestionOption> options = questionOptionMapper.selectList(
                new LambdaQueryWrapper<QuestionOption>()
                        .eq(QuestionOption::getQuestionId, question.getId())
                        .orderByAsc(QuestionOption::getSortOrder, QuestionOption::getOptionKey));
        String userAnswer = normalizeAndValidateAnswer(question.getType(), request.getAnswer(), options);
        boolean correct = question.getAnswer().equals(userAnswer);

        AnswerRecord record = new AnswerRecord();
        record.setUserId(userId);
        record.setQuestionId(question.getId());
        record.setUserAnswer(userAnswer);
        record.setIsCorrect(correct ? 1 : 0);
        answerRecordMapper.insert(record);

        if (!correct) {
            wrongQuestionService.recordWrong(userId, question.getId());
        }

        return new PracticeResultVO(record.getId(), question.getId(), correct, userAnswer,
                question.getAnswer(), question.getAnalysis(),
                favoriteService.isFavorited(userId, question.getId()));
    }

    private String normalizeAndValidateAnswer(String type, String answer, List<QuestionOption> options) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if ("JUDGE".equals(normalizedType)) {
            String normalized = answer.trim().toUpperCase(Locale.ROOT);
            if (!Set.of("TRUE", "FALSE").contains(normalized)) {
                throw new BusinessException("判断题答案只能为TRUE或FALSE");
            }
            return normalized;
        }

        Set<String> validKeys = options.stream()
                .map(QuestionOption::getOptionKey)
                .map(key -> key.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (validKeys.isEmpty()) {
            throw new BusinessException("该题目没有可用选项");
        }

        if ("SINGLE".equals(normalizedType)) {
            String normalized = answer.trim().toUpperCase(Locale.ROOT);
            if (normalized.contains(",") || !validKeys.contains(normalized)) {
                throw new BusinessException("单选题答案必须是一个真实存在的选项");
            }
            return normalized;
        }

        if ("MULTIPLE".equals(normalizedType)) {
            String[] rawParts = answer.split(",", -1);
            List<String> answerParts = new ArrayList<>();
            for (String rawPart : rawParts) {
                String part = rawPart.trim().toUpperCase(Locale.ROOT);
                if (!StringUtils.hasText(part)) {
                    throw new BusinessException("多选题答案不能包含空选项");
                }
                answerParts.add(part);
            }
            Set<String> uniqueAnswers = new HashSet<>(answerParts);
            if (uniqueAnswers.size() != answerParts.size()) {
                throw new BusinessException("多选题答案不能包含重复选项");
            }
            if (!validKeys.containsAll(uniqueAnswers)) {
                throw new BusinessException("多选题答案包含不存在的选项");
            }
            return String.join(",", new TreeSet<>(uniqueAnswers));
        }

        throw new BusinessException("题型不合法");
    }

    private Hierarchy requireEnabledHierarchy(Long knowledgePointId) {
        KnowledgePoint knowledgePoint = knowledgePointMapper.selectById(knowledgePointId);
        if (knowledgePoint == null || !Integer.valueOf(1).equals(knowledgePoint.getStatus())) {
            throw new BusinessException(404, "知识点不存在或未启用");
        }
        Chapter chapter = chapterMapper.selectById(knowledgePoint.getChapterId());
        if (chapter == null || !Integer.valueOf(1).equals(chapter.getStatus())) {
            throw new BusinessException(404, "所属章节不存在或未启用");
        }
        Subject subject = subjectMapper.selectById(chapter.getSubjectId());
        if (subject == null || !Integer.valueOf(1).equals(subject.getStatus())) {
            throw new BusinessException(404, "所属科目不存在或未启用");
        }
        return new Hierarchy(subject, chapter, knowledgePoint);
    }

    private record Hierarchy(Subject subject, Chapter chapter, KnowledgePoint knowledgePoint) {
    }
}
