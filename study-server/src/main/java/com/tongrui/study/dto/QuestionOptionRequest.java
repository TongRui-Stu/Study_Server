package com.tongrui.study.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QuestionOptionRequest {

    @NotBlank(message = "选项标识不能为空")
    @Pattern(regexp = "^[A-Za-z]$", message = "选项标识必须是单个英文字母")
    private String optionKey;

    @NotBlank(message = "选项内容不能为空")
    @Size(max = 5000, message = "选项内容不能超过5000个字符")
    private String content;

    @NotNull(message = "选项排序不能为空")
    @Min(value = 0, message = "选项排序不能小于0")
    private Integer sortOrder;
}
