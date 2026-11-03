package com.tongrui.study.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiChatRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题不能超过2000个字符")
    private String message;

    @Valid
    @Size(max = 16, message = "最多携带最近16条历史消息")
    private List<@NotNull(message = "历史消息项不能为空") AiChatMessageRequest> history = new ArrayList<>();
}
