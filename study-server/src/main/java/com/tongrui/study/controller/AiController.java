package com.tongrui.study.controller;

import com.tongrui.study.common.result.Result;
import com.tongrui.study.client.AiStreamListener;
import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.dto.AiChatRequest;
import com.tongrui.study.dto.AiLearningAnalysisRequest;
import com.tongrui.study.dto.AiQuestionChatRequest;
import com.tongrui.study.service.AiService;
import com.tongrui.study.vo.AiResponseVO;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

@Validated
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;
    private final TaskExecutor aiStreamExecutor;

    public AiController(AiService aiService,
                        @Qualifier("aiStreamExecutor") TaskExecutor aiStreamExecutor) {
        this.aiService = aiService;
        this.aiStreamExecutor = aiStreamExecutor;
    }

    @PostMapping("/chat")
    public Result<AiResponseVO> chat(@AuthenticationPrincipal Long userId,
                                     @Valid @RequestBody AiChatRequest request) {
        return Result.success(aiService.chat(userId, request));
    }

    @PostMapping("/question-chat")
    public Result<AiResponseVO> questionChat(@AuthenticationPrincipal Long userId,
                                             @Valid @RequestBody AiQuestionChatRequest request) {
        return Result.success(aiService.questionChat(userId, request));
    }

    @PostMapping("/learning-analysis")
    public Result<AiResponseVO> learningAnalysis(@AuthenticationPrincipal Long userId,
                                                 @Valid @RequestBody AiLearningAnalysisRequest request) {
        return Result.success(aiService.learningAnalysis(userId, request));
    }

    @PostMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(@AuthenticationPrincipal Long userId,
                                 @Valid @RequestBody AiChatRequest request) {
        return stream(listener -> aiService.chatStream(userId, request, listener));
    }

    @PostMapping(value = "/question-chat/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter questionChatStream(@AuthenticationPrincipal Long userId,
                                         @Valid @RequestBody AiQuestionChatRequest request) {
        return stream(listener -> aiService.questionChatStream(userId, request, listener));
    }

    @PostMapping(value = "/learning-analysis/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter learningAnalysisStream(@AuthenticationPrincipal Long userId,
                                             @Valid @RequestBody AiLearningAnalysisRequest request) {
        return stream(listener -> aiService.learningAnalysisStream(userId, request, listener));
    }

    private SseEmitter stream(StreamAction action) {
        String requestId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(75_000L);
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(error -> closed.set(true));

        if (!send(emitter, closed, "start", Map.of("requestId", requestId))) {
            return emitter;
        }

        try {
            aiStreamExecutor.execute(() -> runStream(action, emitter, closed, requestId));
        } catch (RejectedExecutionException exception) {
            sendError(emitter, closed, requestId, "AI请求较多，请稍后重试");
        }
        return emitter;
    }

    private void runStream(StreamAction action, SseEmitter emitter,
                           AtomicBoolean closed, String requestId) {
        try {
            action.run(new AiStreamListener() {
                @Override
                public void onSource(String provider, String model) {
                    sendOrClose(emitter, closed, "source", Map.of(
                            "requestId", requestId,
                            "provider", provider,
                            "model", model == null ? "" : model));
                }

                @Override
                public void onDelta(String content) {
                    sendOrClose(emitter, closed, "delta", Map.of(
                            "requestId", requestId,
                            "delta", content));
                }
            });
            sendOrClose(emitter, closed, "done", Map.of("requestId", requestId));
        } catch (StreamClosedException ignored) {
            return;
        } catch (BusinessException exception) {
            sendError(emitter, closed, requestId, exception.getMessage());
            return;
        } catch (RuntimeException exception) {
            sendError(emitter, closed, requestId, "AI服务暂时不可用，请稍后重试");
            return;
        }
        if (!closed.get()) {
            emitter.complete();
        }
    }

    private void sendError(SseEmitter emitter, AtomicBoolean closed,
                           String requestId, String message) {
        if (send(emitter, closed, "error", Map.of(
                "requestId", requestId,
                "message", message))) {
            emitter.complete();
        }
    }

    private void sendOrClose(SseEmitter emitter, AtomicBoolean closed,
                             String event, Map<String, String> data) {
        if (!send(emitter, closed, event, data)) {
            throw new StreamClosedException();
        }
    }

    private boolean send(SseEmitter emitter, AtomicBoolean closed,
                         String event, Map<String, String> data) {
        if (closed.get()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
            return true;
        } catch (IOException | IllegalStateException exception) {
            closed.set(true);
            return false;
        }
    }

    @FunctionalInterface
    private interface StreamAction {
        void run(AiStreamListener listener);
    }

    private static final class StreamClosedException extends RuntimeException {
    }
}
