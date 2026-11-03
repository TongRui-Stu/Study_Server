package com.tongrui.study.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiLearningAnalysisRequest {

    @NotNull(message = "统计天数不能为空")
    @Min(value = 7, message = "统计天数不能少于7天")
    @Max(value = 30, message = "统计天数不能超过30天")
    private Integer days;

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题不能超过2000个字符")
    private String message;

    @Valid
    @Size(max = 16, message = "最多携带最近16条历史消息")
    private List<@NotNull(message = "历史消息项不能为空") AiChatMessageRequest> history = new ArrayList<>();
}
