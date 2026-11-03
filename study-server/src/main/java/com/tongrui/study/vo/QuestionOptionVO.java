package com.tongrui.study.vo;

import com.tongrui.study.entity.QuestionOption;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class QuestionOptionVO {
    private Long id;
    private String optionKey;
    private String content;
    private Integer sortOrder;

    public static QuestionOptionVO from(QuestionOption option) {
        return new QuestionOptionVO(option.getId(), option.getOptionKey(),
                option.getContent(), option.getSortOrder());
    }
}
