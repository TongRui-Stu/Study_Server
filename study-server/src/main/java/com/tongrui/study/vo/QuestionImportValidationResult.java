package com.tongrui.study.vo;

import java.util.List;

public record QuestionImportValidationResult(
        boolean valid,
        int totalRows,
        int validRows,
        int invalidRows,
        int duplicateRows,
        int warningCount,
        int omittedErrorCount,
        List<QuestionImportIssue> errors,
        List<QuestionImportIssue> warnings,
        List<QuestionImportPreviewRow> previewRows) {
}
