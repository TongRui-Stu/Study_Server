package com.tongrui.study.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PracticeSubmitRequest {

    @NotNull(message = "题目ID不能为空")
    @Positive(message = "题目ID必须大于0")
    private Long questionId;

    @NotBlank(message = "答案不能为空")
    @Size(max = 255, message = "答案不能超过255个字符")
    private String answer;
}
