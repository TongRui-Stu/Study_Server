package com.tongrui.study.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class KnowledgePointStatisticsVO {
    private Long subjectId;
    private String subjectName;
    private Long chapterId;
    private String chapterName;
    private Long knowledgePointId;
    private String knowledgePointName;
    private Long answerCount;
    private Long correctCount;
    private Long incorrectCount;
    private BigDecimal accuracyRate;
}
