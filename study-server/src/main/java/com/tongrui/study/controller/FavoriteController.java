package com.tongrui.study.controller;

import com.tongrui.study.common.result.Result;
import com.tongrui.study.dto.PersonalQuestionFilter;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.ModelAttribute;
import com.tongrui.study.service.FavoriteService;
import com.tongrui.study.service.PersonalQuestionExcelExportService;
import com.tongrui.study.vo.ExcelExportFile;
import com.tongrui.study.vo.FavoriteVO;
import com.tongrui.study.vo.PageVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final PersonalQuestionExcelExportService excelExportService;

    public FavoriteController(FavoriteService favoriteService,
                              PersonalQuestionExcelExportService excelExportService) {
        this.favoriteService = favoriteService;
        this.excelExportService = excelExportService;
    }

    @GetMapping
    public Result<PageVO<FavoriteVO>> page(
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
            @RequestParam(required = false) @Size(max = 100, message = "题干关键字不能超过100个字符") String keyword,
            @RequestParam(required = false)
            @Pattern(regexp = "(?i)^(ASC|DESC)?$", message = "收藏时间排序只允许ASC或DESC") String favoriteTimeOrder,
            @AuthenticationPrincipal Long userId) {
        return Result.success(favoriteService.page(page, size, userId, subjectId,
                chapterId, knowledgePointId, type, difficulty, keyword, favoriteTimeOrder));
    }

    @PostMapping("/{questionId}")
    public Result<Void> favorite(
            @PathVariable @Positive(message = "题目ID必须大于0") Long questionId,
            @AuthenticationPrincipal Long userId) {
        favoriteService.favorite(userId, questionId);
        return Result.success();
    }

    @DeleteMapping("/{questionId}")
    public Result<Void> unfavorite(
            @PathVariable @Positive(message = "题目ID必须大于0") Long questionId,
            @AuthenticationPrincipal Long userId) {
        favoriteService.unfavorite(userId, questionId);
        return Result.success();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@Valid @ModelAttribute PersonalQuestionFilter filter, @AuthenticationPrincipal Long userId) {
        return excelResponse(excelExportService.exportFavorites(userId, filter));
    }

    private ResponseEntity<byte[]> excelResponse(ExcelExportFile file) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8).build());
        headers.setContentLength(file.content().length);
        return ResponseEntity.ok().headers(headers).body(file.content());
    }
}
