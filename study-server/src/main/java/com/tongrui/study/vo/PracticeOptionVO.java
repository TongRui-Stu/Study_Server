package com.tongrui.study.vo;

import com.tongrui.study.entity.QuestionOption;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PracticeOptionVO {

    private String optionKey;
    private String content;

    public static PracticeOptionVO from(QuestionOption option) {
        return new PracticeOptionVO(option.getOptionKey(), option.getContent());
    }
}
