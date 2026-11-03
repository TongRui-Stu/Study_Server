package com.tongrui.study.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StatisticsSummaryVO {
    private Long totalAnswerCount;
    private Long correctAnswerCount;
    private Long incorrectAnswerCount;
    private BigDecimal accuracyRate;
    private Long answeredQuestionCount;
    private Long wrongQuestionCount;
    private Long favoriteCount;
    private Long studyDays;
}
