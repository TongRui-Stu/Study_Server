package com.tongrui.study.dto;

public record QuestionExportFilter(Long subjectId, Long chapterId, Long knowledgePointId,
                                   String type, Integer difficulty, Integer status, String keyword) {
}
