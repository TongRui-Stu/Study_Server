package com.tongrui.study.controller;

import com.tongrui.study.common.result.Result;
import com.tongrui.study.dto.PracticeSubmitRequest;
import com.tongrui.study.dto.PersonalQuestionFilter;
import org.springframework.web.bind.annotation.ModelAttribute;
import com.tongrui.study.enums.PracticeSource;
import com.tongrui.study.service.PracticeService;
import com.tongrui.study.vo.PracticeQuestionListVO;
import com.tongrui.study.vo.PracticeResultVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/practice")
public class PracticeController {

    private final PracticeService practiceService;

    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    @GetMapping("/questions")
    public Result<PracticeQuestionListVO> listQuestions(
            @RequestParam(defaultValue = "KNOWLEDGE_POINT") String source,
            @RequestParam(required = false) @Positive(message = "知识点ID必须大于0") Long knowledgePointId,
            @Valid @ModelAttribute PersonalQuestionFilter filter,
            @AuthenticationPrincipal Long userId) {
        return Result.success(practiceService.listQuestions(
                PracticeSource.from(source), knowledgePointId, userId, filter));
    }

    @PostMapping("/submit")
    public Result<PracticeResultVO> submit(@Valid @RequestBody PracticeSubmitRequest request,
                                           @AuthenticationPrincipal Long userId) {
        return Result.success(practiceService.submit(request, userId));
    }
}
