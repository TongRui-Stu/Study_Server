package com.tongrui.study.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class QuestionSaveRequest {

    @NotNull(message = "所属知识点不能为空")
    @Positive(message = "知识点ID必须大于0")
    private Long knowledgePointId;

    @NotBlank(message = "题型不能为空")
    @Pattern(regexp = "(?i)^(SINGLE|MULTIPLE|JUDGE)$", message = "题型不合法")
    private String type;

    @NotBlank(message = "题干不能为空")
    @Size(max = 10000, message = "题干不能超过10000个字符")
    private String content;

    @NotBlank(message = "正确答案不能为空")
    @Size(max = 255, message = "正确答案不能超过255个字符")
    private String answer;

    @Size(max = 10000, message = "题目解析不能超过10000个字符")
    private String analysis;

    @NotNull(message = "难度不能为空")
    @Min(value = 1, message = "难度只能为1、2或3")
    @Max(value = 3, message = "难度只能为1、2或3")
    private Integer difficulty;

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能为0或1")
    @Max(value = 1, message = "状态只能为0或1")
    private Integer status;

    @Valid
    private List<QuestionOptionRequest> options;
}
