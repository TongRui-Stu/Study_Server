package com.tongrui.study.controller;

import com.tongrui.study.common.result.Result;
import com.tongrui.study.dto.KnowledgePointSaveRequest;
import com.tongrui.study.dto.StatusUpdateRequest;
import com.tongrui.study.service.KnowledgePointService;
import com.tongrui.study.vo.KnowledgePointVO;
import com.tongrui.study.vo.PageVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
public class KnowledgePointController {

    private final KnowledgePointService knowledgePointService;

    public KnowledgePointController(KnowledgePointService knowledgePointService) {
        this.knowledgePointService = knowledgePointService;
    }

    @GetMapping("/api/chapters/{chapterId}/knowledge-points")
    public Result<List<KnowledgePointVO>> listEnabled(
            @PathVariable @Positive(message = "章节ID必须大于0") Long chapterId) {
        return Result.success(knowledgePointService.listEnabledByChapter(chapterId));
    }

    @GetMapping("/api/admin/knowledge-points")
    public Result<PageVO<KnowledgePointVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量必须大于0")
            @Max(value = 100, message = "每页数量不能超过100") long size,
            @RequestParam(required = false) @Positive(message = "科目ID必须大于0") Long subjectId,
            @RequestParam(required = false) @Positive(message = "章节ID必须大于0") Long chapterId,
            @RequestParam(required = false) String keyword) {
        return Result.success(knowledgePointService.page(current, size, subjectId, chapterId, keyword));
    }

    @PostMapping("/api/admin/knowledge-points")
    public Result<KnowledgePointVO> create(@Valid @RequestBody KnowledgePointSaveRequest request) {
        return Result.success(knowledgePointService.create(request));
    }

    @PutMapping("/api/admin/knowledge-points/{id}")
    public Result<KnowledgePointVO> update(
            @PathVariable @Positive(message = "知识点ID必须大于0") Long id,
            @Valid @RequestBody KnowledgePointSaveRequest request) {
        return Result.success(knowledgePointService.update(id, request));
    }

    @PutMapping("/api/admin/knowledge-points/{id}/status")
    public Result<Void> updateStatus(@PathVariable @Positive(message = "知识点ID必须大于0") Long id,
                                     @Valid @RequestBody StatusUpdateRequest request) {
        knowledgePointService.updateStatus(id, request.getStatus());
        return Result.success();
    }

    @DeleteMapping("/api/admin/knowledge-points/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "知识点ID必须大于0") Long id) {
        knowledgePointService.delete(id);
        return Result.success();
    }
}
