package com.tongrui.study.controller;

import com.tongrui.study.common.result.Result;
import com.tongrui.study.dto.QuestionSaveRequest;
import com.tongrui.study.dto.QuestionExportFilter;
import com.tongrui.study.dto.StatusUpdateRequest;
import com.tongrui.study.service.QuestionExcelExportService;
import com.tongrui.study.service.QuestionService;
import com.tongrui.study.vo.ExcelExportFile;
import com.tongrui.study.vo.PageVO;
import com.tongrui.study.vo.QuestionVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

@Validated
@RestController
public class QuestionController {

    private final QuestionService questionService;
    private final QuestionExcelExportService questionExcelExportService;

    public QuestionController(QuestionService questionService,
                              QuestionExcelExportService questionExcelExportService) {
        this.questionService = questionService;
        this.questionExcelExportService = questionExcelExportService;
    }

    @GetMapping("/api/admin/questions")
    public Result<PageVO<QuestionVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量必须大于0")
            @Max(value = 100, message = "每页数量不能超过100") long size,
            @RequestParam(required = false) @Positive(message = "科目ID必须大于0") Long subjectId,
            @RequestParam(required = false) @Positive(message = "章节ID必须大于0") Long chapterId,
            @RequestParam(required = false) @Positive(message = "知识点ID必须大于0") Long knowledgePointId,
            @RequestParam(required = false)
            @Pattern(regexp = "(?i)^(SINGLE|MULTIPLE|JUDGE)$", message = "题型不合法") String type,
            @RequestParam(required = false) @Min(value = 1, message = "难度只能为1、2或3")
            @Max(value = 3, message = "难度只能为1、2或3") Integer difficulty,
            @RequestParam(required = false) @Min(value = 0, message = "状态只能为0或1")
            @Max(value = 1, message = "状态只能为0或1") Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.success(questionService.page(page, size, subjectId, chapterId,
                knowledgePointId, type, difficulty, status, keyword));
    }

    @GetMapping("/api/admin/questions/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @Positive(message = "科目ID必须大于0") Long subjectId,
            @RequestParam(required = false) @Positive(message = "章节ID必须大于0") Long chapterId,
            @RequestParam(required = false) @Positive(message = "知识点ID必须大于0") Long knowledgePointId,
            @RequestParam(required = false)
            @Pattern(regexp = "(?i)^(SINGLE|MULTIPLE|JUDGE)$", message = "题型不合法") String type,
            @RequestParam(required = false) @Min(value = 1, message = "难度只能为1、2或3")
            @Max(value = 3, message = "难度只能为1、2或3") Integer difficulty,
            @RequestParam(required = false) @Min(value = 0, message = "状态只能为0或1")
            @Max(value = 1, message = "状态只能为0或1") Integer status,
            @RequestParam(required = false) String keyword) {
        ExcelExportFile file = questionExcelExportService.export(new QuestionExportFilter(
                subjectId, chapterId, knowledgePointId, type, difficulty, status, keyword));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8).build());
        headers.setContentLength(file.content().length);
        return ResponseEntity.ok().headers(headers).body(file.content());
    }

    @GetMapping("/api/admin/questions/{id}")
    public Result<QuestionVO> getById(@PathVariable @Positive(message = "题目ID必须大于0") Long id) {
        return Result.success(questionService.getById(id));
    }

    @PostMapping("/api/admin/questions")
    public Result<QuestionVO> create(@Valid @RequestBody QuestionSaveRequest request,
                                     Authentication authentication) {
        return Result.success(questionService.create(request, (Long) authentication.getPrincipal()));
    }

    @PutMapping("/api/admin/questions/{id}")
    public Result<QuestionVO> update(@PathVariable @Positive(message = "题目ID必须大于0") Long id,
                                     @Valid @RequestBody QuestionSaveRequest request) {
        return Result.success(questionService.update(id, request));
    }

    @PutMapping("/api/admin/questions/{id}/status")
    public Result<Void> updateStatus(@PathVariable @Positive(message = "题目ID必须大于0") Long id,
                                     @Valid @RequestBody StatusUpdateRequest request) {
        questionService.updateStatus(id, request.getStatus());
        return Result.success();
    }

    @DeleteMapping("/api/admin/questions/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "题目ID必须大于0") Long id) {
        questionService.delete(id);
        return Result.success();
    }
}
