package com.tongrui.study.controller;

import com.tongrui.study.common.result.Result;
import com.tongrui.study.dto.StatusUpdateRequest;
import com.tongrui.study.dto.SubjectSaveRequest;
import com.tongrui.study.service.SubjectService;
import com.tongrui.study.vo.PageVO;
import com.tongrui.study.vo.SubjectVO;
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
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping("/api/subjects")
    public Result<List<SubjectVO>> listEnabled() {
        return Result.success(subjectService.listEnabled());
    }

    @GetMapping("/api/admin/subjects")
    public Result<PageVO<SubjectVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量必须大于0")
            @Max(value = 100, message = "每页数量不能超过100") long size,
            @RequestParam(required = false) String keyword) {
        return Result.success(subjectService.page(current, size, keyword));
    }

    @PostMapping("/api/admin/subjects")
    public Result<SubjectVO> create(@Valid @RequestBody SubjectSaveRequest request) {
        return Result.success(subjectService.create(request));
    }

    @PutMapping("/api/admin/subjects/{id}")
    public Result<SubjectVO> update(@PathVariable @Positive(message = "科目ID必须大于0") Long id,
                                    @Valid @RequestBody SubjectSaveRequest request) {
        return Result.success(subjectService.update(id, request));
    }

    @PutMapping("/api/admin/subjects/{id}/status")
    public Result<Void> updateStatus(@PathVariable @Positive(message = "科目ID必须大于0") Long id,
                                     @Valid @RequestBody StatusUpdateRequest request) {
        subjectService.updateStatus(id, request.getStatus());
        return Result.success();
    }

    @DeleteMapping("/api/admin/subjects/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "科目ID必须大于0") Long id) {
        subjectService.delete(id);
        return Result.success();
    }
}
