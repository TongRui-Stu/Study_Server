package com.tongrui.study.service;

import com.tongrui.study.client.AiStreamListener;
import com.tongrui.study.client.AiStreamResult;
import com.tongrui.study.dto.AiChatRequest;
import com.tongrui.study.dto.AiLearningAnalysisRequest;
import com.tongrui.study.dto.AiQuestionChatRequest;
import com.tongrui.study.vo.AiResponseVO;

public interface AiService {

    AiResponseVO chat(Long userId, AiChatRequest request);

    AiResponseVO questionChat(Long userId, AiQuestionChatRequest request);

    AiResponseVO learningAnalysis(Long userId, AiLearningAnalysisRequest request);

    AiStreamResult chatStream(Long userId, AiChatRequest request, AiStreamListener listener);

    AiStreamResult questionChatStream(Long userId, AiQuestionChatRequest request, AiStreamListener listener);

    AiStreamResult learningAnalysisStream(Long userId, AiLearningAnalysisRequest request,
                                          AiStreamListener listener);
}
