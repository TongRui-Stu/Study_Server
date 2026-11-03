package com.tongrui.study.vo;

import com.tongrui.study.enums.PracticeSource;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PracticeQuestionListVO {

    private PracticeSource source;
    private Long knowledgePointId;
    private String knowledgePointName;
    private List<PracticeQuestionVO> questions;
}
