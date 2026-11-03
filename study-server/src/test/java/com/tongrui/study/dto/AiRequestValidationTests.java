package com.tongrui.study.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRequestValidationTests {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void rejectsBlankAndOversizedMessage() {
        AiChatRequest blank = new AiChatRequest();
        blank.setMessage("  ");
        assertFalse(validator.validate(blank).isEmpty());

        AiChatRequest oversized = new AiChatRequest();
        oversized.setMessage("x".repeat(2001));
        assertFalse(validator.validate(oversized).isEmpty());
    }

    @Test
    void allowsSixteenHistoryMessagesAndRejectsSeventeen() {
        AiChatRequest allowed = new AiChatRequest();
        allowed.setMessage("问题");
        List<AiChatMessageRequest> sixteenMessages = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            sixteenMessages.add(message(index % 2 == 0 ? "user" : "assistant", "内容"));
        }
        allowed.setHistory(sixteenMessages);
        assertTrue(validator.validate(allowed).isEmpty());

        AiChatRequest tooMany = new AiChatRequest();
        tooMany.setMessage("问题");
        List<AiChatMessageRequest> messages = new ArrayList<>();
        for (int index = 0; index < 17; index++) {
            messages.add(message("user", "内容"));
        }
        tooMany.setHistory(messages);
        assertFalse(validator.validate(tooMany).isEmpty());

        AiQuestionChatRequest questionAllowed = new AiQuestionChatRequest();
        questionAllowed.setAnswerRecordId(1L);
        questionAllowed.setMessage("追问");
        questionAllowed.setHistory(sixteenMessages);
        assertTrue(validator.validate(questionAllowed).isEmpty());

        AiQuestionChatRequest questionTooMany = new AiQuestionChatRequest();
        questionTooMany.setAnswerRecordId(1L);
        questionTooMany.setMessage("追问");
        questionTooMany.setHistory(messages);
        assertFalse(validator.validate(questionTooMany).isEmpty());
    }

    @Test
    void rejectsOversizedHistoryContentAndNullItem() {
        AiChatRequest longAssistantHistory = new AiChatRequest();
        longAssistantHistory.setMessage("问题");
        longAssistantHistory.setHistory(List.of(message("assistant", "x".repeat(6000))));
        assertTrue(validator.validate(longAssistantHistory).isEmpty());

        AiChatRequest oversizedHistory = new AiChatRequest();
        oversizedHistory.setMessage("问题");
        oversizedHistory.setHistory(List.of(message("assistant", "x".repeat(12001))));
        assertFalse(validator.validate(oversizedHistory).isEmpty());

        AiChatRequest nullHistoryItem = new AiChatRequest();
        nullHistoryItem.setMessage("问题");
        List<AiChatMessageRequest> historyWithNull = new ArrayList<>();
        historyWithNull.add(null);
        nullHistoryItem.setHistory(historyWithNull);
        assertFalse(validator.validate(nullHistoryItem).isEmpty());
    }

    @Test
    void validatesLearningAnalysisRequestRangeMessageAndHistoryCount() {
        AiLearningAnalysisRequest valid = learningRequest(7, "生成诊断", List.of());
        assertTrue(validator.validate(valid).isEmpty());

        assertFalse(validator.validate(learningRequest(6, "生成诊断", List.of())).isEmpty());
        assertFalse(validator.validate(learningRequest(31, "生成诊断", List.of())).isEmpty());
        assertFalse(validator.validate(learningRequest(7, " ", List.of())).isEmpty());

        List<AiChatMessageRequest> tooMany = new ArrayList<>();
        for (int index = 0; index < 17; index++) {
            tooMany.add(message(index % 2 == 0 ? "user" : "assistant", "内容"));
        }
        assertFalse(validator.validate(learningRequest(30, "继续", tooMany)).isEmpty());
    }

    private AiLearningAnalysisRequest learningRequest(int days, String content,
                                                      List<AiChatMessageRequest> history) {
        AiLearningAnalysisRequest request = new AiLearningAnalysisRequest();
        request.setDays(days);
        request.setMessage(content);
        request.setHistory(history);
        return request;
    }

    private AiChatMessageRequest message(String role, String content) {
        AiChatMessageRequest message = new AiChatMessageRequest();
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
