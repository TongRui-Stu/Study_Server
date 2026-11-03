package com.tongrui.study.dto;

import com.tongrui.study.common.exception.BusinessException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Locale;

/** Shared query semantics for personal lists, practice and exports. */
@Data
public class PersonalQuestionFilter {
    @Positive(message = "科目ID必须大于0")
    private Long subjectId;
    @Positive(message = "章节ID必须大于0")
    private Long chapterId;
    @Positive(message = "知识点ID必须大于0")
    private Long knowledgePointId;
    @Size(max = 100, message = "题干关键字不能超过100个字符")
    private String keyword;
    @Pattern(regexp = "(?i)^(ASC|DESC)?$", message = "累计错误排序只允许ASC或DESC")
    private String wrongCountOrder;
    @Pattern(regexp = "(?i)^(ASC|DESC)?$", message = "收藏时间排序只允许ASC或DESC")
    private String favoriteTimeOrder;
    @Pattern(regexp = "(?i)^(SINGLE|MULTIPLE|JUDGE)$", message = "题型不合法")
    private String type;
    @Min(1) @Max(3)
    private Integer difficulty;

    public static PersonalQuestionFilter of(Long subjectId, Long chapterId, Long knowledgePointId,
                                            String type, Integer difficulty, String keyword, String order) {
        PersonalQuestionFilter filter = new PersonalQuestionFilter();
        filter.subjectId = subjectId;
        filter.chapterId = chapterId;
        filter.knowledgePointId = knowledgePointId;
        filter.type = type;
        filter.difficulty = difficulty;
        filter.keyword = keyword;
        filter.wrongCountOrder = order;
        return filter.normalized();
    }

    public PersonalQuestionFilter normalized() {
        if ((subjectId != null && subjectId <= 0) || (chapterId != null && chapterId <= 0)
                || (knowledgePointId != null && knowledgePointId <= 0)) {
            throw new BusinessException("筛选ID必须大于0");
        }
        String text = keyword == null ? null : keyword.trim();
        if (text != null && text.length() > 100) throw new BusinessException("题干关键字不能超过100个字符");
        String order = wrongCountOrder == null ? null : wrongCountOrder.trim().toUpperCase(Locale.ROOT);
        if (order != null && !order.isEmpty() && !order.equals("ASC") && !order.equals("DESC")) {
            throw new BusinessException("累计错误排序只允许ASC或DESC");
        }
        String favoriteOrder = favoriteTimeOrder == null ? null : favoriteTimeOrder.trim().toUpperCase(Locale.ROOT);
        if (favoriteOrder != null && !favoriteOrder.isEmpty()
                && !favoriteOrder.equals("ASC") && !favoriteOrder.equals("DESC")) {
            throw new BusinessException("收藏时间排序只允许ASC或DESC");
        }
        String questionType = type == null ? null : type.trim().toUpperCase(Locale.ROOT);
        if (questionType != null && !questionType.isEmpty()
                && !java.util.Set.of("SINGLE", "MULTIPLE", "JUDGE").contains(questionType)) {
            throw new BusinessException("题型不合法");
        }
        if (difficulty != null && (difficulty < 1 || difficulty > 3)) throw new BusinessException("难度不合法");
        PersonalQuestionFilter result = new PersonalQuestionFilter();
        result.subjectId = subjectId;
        result.chapterId = chapterId;
        result.knowledgePointId = knowledgePointId;
        result.keyword = text == null || text.isEmpty() ? null : text;
        result.wrongCountOrder = order == null || order.isEmpty() ? null : order;
        result.favoriteTimeOrder = favoriteOrder == null || favoriteOrder.isEmpty() ? null : favoriteOrder;
        result.type = questionType == null || questionType.isEmpty() ? null : questionType;
        result.difficulty = difficulty;
        return result;
    }
}
