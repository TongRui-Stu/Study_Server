package com.tongrui.study.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SubjectStatisticsVO {
    private Long subjectId;
    private String subjectName;
    private Long answerCount;
    private Long correctCount;
    private Long incorrectCount;
    private BigDecimal accuracyRate;
}
