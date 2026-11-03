package com.tongrui.study.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PracticeQuestionVO {

    private Long id;
    private String type;
    private String content;
    private Integer difficulty;
    private List<PracticeOptionVO> options;
}
