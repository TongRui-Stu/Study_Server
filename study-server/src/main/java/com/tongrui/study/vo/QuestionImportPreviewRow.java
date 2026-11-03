package com.tongrui.study.vo;

public record QuestionImportPreviewRow(
        int rowNumber,
        Long knowledgePointId,
        String subjectName,
        String chapterName,
        String knowledgePointName,
        String type,
        Integer difficulty,
        Integer status,
        String content,
        String answer,
        int optionCount) {
}
