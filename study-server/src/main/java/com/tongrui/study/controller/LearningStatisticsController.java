package com.tongrui.study.controller;

import com.tongrui.study.common.result.Result;
import com.tongrui.study.service.LearningStatisticsService;
import com.tongrui.study.service.LearningStatisticsExcelService;
import com.tongrui.study.vo.ExcelExportFile;
import com.tongrui.study.vo.LearningStatisticsVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

@Validated
@RestController
@RequestMapping("/api/statistics")
public class LearningStatisticsController {

    private final LearningStatisticsService learningStatisticsService;
    private final LearningStatisticsExcelService learningStatisticsExcelService;

    public LearningStatisticsController(LearningStatisticsService learningStatisticsService,
                                        LearningStatisticsExcelService learningStatisticsExcelService) {
        this.learningStatisticsService = learningStatisticsService;
        this.learningStatisticsExcelService = learningStatisticsExcelService;
    }

    @GetMapping("/overview")
    public Result<LearningStatisticsVO> overview(
            @RequestParam(defaultValue = "7")
            @Min(value = 7, message = "统计天数不能少于7天")
            @Max(value = 30, message = "统计天数不能超过30天") int days,
            @AuthenticationPrincipal Long userId) {
        return Result.success(learningStatisticsService.overview(userId, days));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "7")
            @Min(value = 7, message = "统计天数不能少于7天")
            @Max(value = 30, message = "统计天数不能超过30天") int days,
            @AuthenticationPrincipal Long userId) {
        ExcelExportFile file = learningStatisticsExcelService.export(userId, days);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8).build());
        headers.setContentLength(file.content().length);
        return ResponseEntity.ok().headers(headers).body(file.content());
    }
}
