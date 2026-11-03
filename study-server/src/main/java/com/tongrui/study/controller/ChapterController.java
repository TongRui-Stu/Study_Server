package com.tongrui.study.controller;

import com.tongrui.study.common.result.Result;
import com.tongrui.study.dto.ChapterSaveRequest;
import com.tongrui.study.dto.StatusUpdateRequest;
import com.tongrui.study.service.ChapterService;
import com.tongrui.study.vo.ChapterVO;
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
public class ChapterController {

    private final ChapterService chapterService;

    public ChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @GetMapping("/api/subjects/{subjectId}/chapters")
    public Result<List<ChapterVO>> listEnabled(
            @PathVariable @Positive(message = "科目ID必须大于0") Long subjectId) {
        return Result.success(chapterService.listEnabledBySubject(subjectId));
    }

    @GetMapping("/api/admin/chapters")
    public Result<PageVO<ChapterVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量必须大于0")
            @Max(value = 100, message = "每页数量不能超过100") long size,
            @RequestParam(required = false) @Positive(message = "科目ID必须大于0") Long subjectId,
            @RequestParam(required = false) String keyword) {
        return Result.success(chapterService.page(current, size, subjectId, keyword));
    }

    @PostMapping("/api/admin/chapters")
    public Result<ChapterVO> create(@Valid @RequestBody ChapterSaveRequest request) {
        return Result.success(chapterService.create(request));
    }

    @PutMapping("/api/admin/chapters/{id}")
    public Result<ChapterVO> update(@PathVariable @Positive(message = "章节ID必须大于0") Long id,
                                    @Valid @RequestBody ChapterSaveRequest request) {
        return Result.success(chapterService.update(id, request));
    }

    @PutMapping("/api/admin/chapters/{id}/status")
    public Result<Void> updateStatus(@PathVariable @Positive(message = "章节ID必须大于0") Long id,
                                     @Valid @RequestBody StatusUpdateRequest request) {
        chapterService.updateStatus(id, request.getStatus());
        return Result.success();
    }

    @DeleteMapping("/api/admin/chapters/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "章节ID必须大于0") Long id) {
        chapterService.delete(id);
        return Result.success();
    }
}
