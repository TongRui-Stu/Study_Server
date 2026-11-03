package com.tongrui.study.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KnowledgePointSaveRequest {

    @NotNull(message = "所属章节不能为空")
    @Positive(message = "所属章节ID必须大于0")
    private Long chapterId;

    @NotBlank(message = "知识点名称不能为空")
    @Size(max = 100, message = "知识点名称不能超过100个字符")
    private String name;

    @Size(max = 500, message = "知识点描述不能超过500个字符")
    private String description;

    @NotNull(message = "排序不能为空")
    @Min(value = 0, message = "排序不能小于0")
    private Integer sortOrder;

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能为0或1")
    @Max(value = 1, message = "状态只能为0或1")
    private Integer status;
}
