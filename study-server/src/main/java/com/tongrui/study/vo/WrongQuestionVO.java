package com.tongrui.study.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WrongQuestionVO {
    private Long questionId;
    private String type;
    private String content;
    private Integer difficulty;
    private List<PracticeOptionVO> options;
    private String correctAnswer;
    private String analysis;
    private Long subjectId;
    private String subjectName;
    private Long chapterId;
    private String chapterName;
    private Long knowledgePointId;
    private String knowledgePointName;
    private Integer wrongCount;
    private LocalDateTime lastWrongAt;
    private Integer questionStatus;
}
