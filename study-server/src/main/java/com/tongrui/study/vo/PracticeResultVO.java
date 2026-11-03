package com.tongrui.study.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PracticeResultVO {

    private Long answerRecordId;
    private Long questionId;
    private Boolean isCorrect;
    private String userAnswer;
    private String correctAnswer;
    private String analysis;
    private Boolean favorited;
}
