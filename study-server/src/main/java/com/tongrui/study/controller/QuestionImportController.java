package com.tongrui.study.controller;

import com.tongrui.study.common.result.Result;
import com.tongrui.study.service.QuestionImportService;
import com.tongrui.study.vo.ExcelExportFile;
import com.tongrui.study.vo.QuestionImportResult;
import com.tongrui.study.vo.QuestionImportValidationResult;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admin/questions/import")
public class QuestionImportController {

    private final QuestionImportService questionImportService;

    public QuestionImportController(QuestionImportService questionImportService) {
        this.questionImportService = questionImportService;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        ExcelExportFile file = questionImportService.createTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8).build());
        headers.setContentLength(file.content().length);
        return ResponseEntity.ok().headers(headers).body(file.content());
    }

    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<QuestionImportValidationResult> validate(@RequestParam("file") MultipartFile file) {
        return Result.success(questionImportService.validate(file));
    }

    @PostMapping(value = "/execute", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<QuestionImportResult> execute(@RequestParam("file") MultipartFile file,
                                                Authentication authentication) {
        return Result.success(questionImportService.execute(file, (Long) authentication.getPrincipal()));
    }
}
