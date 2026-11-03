package com.tongrui.study.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class QuestionVO {
    private Long id;
    private Long subjectId;
    private String subjectName;
    private Long chapterId;
    private String chapterName;
    private Long knowledgePointId;
    private String knowledgePointName;
    private String type;
    private String content;
    private String answer;
    private String analysis;
    private Integer difficulty;
    private Integer status;
    private List<QuestionOptionVO> options;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
