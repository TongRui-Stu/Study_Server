package com.tongrui.study.util;

import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.dto.QuestionOptionRequest;
import com.tongrui.study.dto.QuestionSaveRequest;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Shared normalization and business validation for manual creation and Excel import. */
public final class QuestionValidationSupport {

    private static final Set<String> QUESTION_TYPES = Set.of("SINGLE", "MULTIPLE", "JUDGE");

    private QuestionValidationSupport() {
    }

    public static PreparedQuestion prepare(QuestionSaveRequest request) {
        String type = normalizeType(request.getType());
        if ("JUDGE".equals(type)) {
            if (request.getOptions() != null && !request.getOptions().isEmpty()) {
                throw new BusinessException("判断题不能填写选项");
            }
            String answer = text(request.getAnswer()).toUpperCase(Locale.ROOT);
            if (!Set.of("TRUE", "FALSE").contains(answer)) {
                throw new BusinessException("判断题答案只能为TRUE或FALSE");
            }
            return new PreparedQuestion(type, answer, List.of());
        }

        List<QuestionOptionRequest> requestedOptions = request.getOptions();
        if (requestedOptions == null || requestedOptions.size() < 2) {
            throw new BusinessException("选择题至少需要2个选项");
        }

        Set<String> optionKeys = new HashSet<>();
        List<NormalizedOption> options = new ArrayList<>();
        for (int index = 0; index < requestedOptions.size(); index++) {
            QuestionOptionRequest option = requestedOptions.get(index);
            String key = text(option.getOptionKey()).toUpperCase(Locale.ROOT);
            if (!optionKeys.add(key)) {
                throw new BusinessException("选项标识不能重复");
            }
            String expectedKey = String.valueOf((char) ('A' + index));
            if (!expectedKey.equals(key)) {
                throw new BusinessException("选项必须从A开始连续填写");
            }
            String content = text(option.getContent());
            if (content.isEmpty()) {
                throw new BusinessException("选项内容不能为空");
            }
            options.add(new NormalizedOption(key, content, option.getSortOrder()));
        }

        List<String> answerParts = List.of(text(request.getAnswer()).split(","))
                .stream().map(String::trim)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .toList();
        if (answerParts.isEmpty()) {
            throw new BusinessException("正确答案不能为空");
        }
        Set<String> uniqueAnswers = new HashSet<>(answerParts);
        if (uniqueAnswers.size() != answerParts.size()) {
            throw new BusinessException("正确答案不能包含重复选项");
        }
        if ("SINGLE".equals(type) && answerParts.size() != 1) {
            throw new BusinessException("单选题只能设置一个正确答案");
        }
        if ("MULTIPLE".equals(type) && answerParts.size() < 2) {
            throw new BusinessException("多选题至少需要2个正确答案");
        }
        if (!optionKeys.containsAll(uniqueAnswers)) {
            throw new BusinessException("正确答案必须对应真实选项");
        }

        String answer = String.join(",", new TreeSet<>(uniqueAnswers));
        options.sort(Comparator.comparing(NormalizedOption::sortOrder)
                .thenComparing(NormalizedOption::optionKey));
        return new PreparedQuestion(type, answer, options);
    }

    public static String normalizeType(String type) {
        String normalized = text(type).toUpperCase(Locale.ROOT);
        if (!QUESTION_TYPES.contains(normalized)) {
            throw new BusinessException("题型不合法");
        }
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    public record NormalizedOption(String optionKey, String content, Integer sortOrder) {
    }

    public record PreparedQuestion(String type, String answer, List<NormalizedOption> options) {
    }
}
