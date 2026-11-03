package com.tongrui.study.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiChatMessageRequest {

    @NotBlank(message = "历史消息角色不能为空")
    @Size(max = 20, message = "历史消息角色过长")
    private String role;

    @NotBlank(message = "历史消息内容不能为空")
    @Size(max = 12000, message = "单条历史消息不能超过12000个字符")
    private String content;
}
