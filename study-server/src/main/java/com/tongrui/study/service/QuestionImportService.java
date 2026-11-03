package com.tongrui.study.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.dto.QuestionOptionRequest;
import com.tongrui.study.dto.QuestionSaveRequest;
import com.tongrui.study.entity.Chapter;
import com.tongrui.study.entity.KnowledgePoint;
import com.tongrui.study.entity.Question;
import com.tongrui.study.entity.Subject;
import com.tongrui.study.mapper.ChapterMapper;
import com.tongrui.study.mapper.KnowledgePointMapper;
import com.tongrui.study.mapper.QuestionMapper;
import com.tongrui.study.mapper.SubjectMapper;
import com.tongrui.study.util.ExcelExportSupport;
import com.tongrui.study.util.QuestionValidationSupport;
import com.tongrui.study.vo.ExcelExportFile;
import com.tongrui.study.vo.QuestionImportIssue;
import com.tongrui.study.vo.QuestionImportPreviewRow;
import com.tongrui.study.vo.QuestionImportResult;
import com.tongrui.study.vo.QuestionImportValidationResult;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuestionImportService {

    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    public static final int MAX_QUESTION_ROWS = 1000;
    private static final int MAX_RETURNED_ISSUES = 100;
    private static final int INPUT_HEADER_ROW = 1;
    private static final int INPUT_DATA_START_ROW = 2;
    private static final int HELPER_START_COLUMN = 7;
    private static final String HIERARCHY_SEPARATOR = "｜";
    private static final String VALID_SUBJECTS_NAME = "ValidSubjects";
    private static final String EMPTY_HIERARCHY_OPTIONS_NAME = "EmptyHierarchyOptions";
    private static final String CHAPTERS_NAME_PREFIX = "ChaptersForSubject_";
    private static final String KNOWLEDGE_NAME_PREFIX = "KnowledgeForChapter_";
    private static final String INPUT_SHEET = "题目录入";
    private static final String[] SHEET_NAMES = {INPUT_SHEET, "填写示例", "填写说明", "知识点清单"};
    private static final String[] HEADERS = {
            "知识点ID", "科目", "章节", "知识点", "题型", "难度", "状态", "题干",
            "选项A", "选项B", "选项C", "选项D", "选项E", "选项F", "选项G", "选项H",
            "正确答案", "解析"
    };
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Map<String, String> TYPE_ALIASES = Map.of(
            "单选题", "SINGLE", "SINGLE", "SINGLE",
            "多选题", "MULTIPLE", "MULTIPLE", "MULTIPLE",
            "判断题", "JUDGE", "JUDGE", "JUDGE");
    private static final Map<String, Integer> DIFFICULTY_ALIASES = Map.of(
            "简单", 1, "1", 1, "中等", 2, "2", 2, "困难", 3, "3", 3);
    private static final Map<String, Integer> STATUS_ALIASES = Map.of(
            "启用", 1, "1", 1, "禁用", 0, "0", 0);

    private final SubjectMapper subjectMapper;
    private final ChapterMapper chapterMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final QuestionMapper questionMapper;
    private final QuestionImportPersistence persistence;

    public QuestionImportService(SubjectMapper subjectMapper,
                                 ChapterMapper chapterMapper,
                                 KnowledgePointMapper knowledgePointMapper,
                                 QuestionMapper questionMapper,
                                 QuestionImportPersistence persistence) {
        this.subjectMapper = subjectMapper;
        this.chapterMapper = chapterMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.questionMapper = questionMapper;
        this.persistence = persistence;
    }

    public ExcelExportFile createTemplate() {
        HierarchySnapshot hierarchy = loadHierarchy();
        Workbook workbook = new XSSFWorkbook();
        ExcelExportSupport.applyDefaultFont(workbook);
        ExcelExportSupport.Styles styles = ExcelExportSupport.createStyles(workbook);
        createInputSheet(workbook, styles);
        createExampleSheet(workbook, styles, hierarchy);
        createInstructionSheet(workbook, styles, hierarchy.entries().stream().noneMatch(this::isActive));
        createKnowledgeSheet(workbook, styles, hierarchy);
        addHierarchyDropdowns(workbook, workbook.getSheet(INPUT_SHEET),
                workbook.getSheet("知识点清单"), styles, hierarchy);
        return new ExcelExportFile("题库批量导入模板_" + LocalDateTime.now().format(FILE_TIME) + ".xlsx",
                ExcelExportSupport.toBytes(workbook));
    }

    public QuestionImportValidationResult validate(MultipartFile file) {
        return validateInternal(file).result();
    }

    @Transactional
    public QuestionImportResult execute(MultipartFile file, Long createdBy) {
        ValidatedImport validated = validateInternal(file);
        if (!validated.result().valid()) {
            String message = validated.result().errors().isEmpty()
                    ? "文件预校验失败"
                    : validated.result().errors().get(0).message();
            throw new BusinessException("正式导入重新校验失败：" + message);
        }

        int optionCount = 0;
        for (PreparedRow row : validated.rows()) {
            optionCount += persistence.persist(row.request(), row.prepared(), createdBy);
        }
        return new QuestionImportResult(validated.rows().size(), optionCount);
    }

    private ValidatedImport validateInternal(MultipartFile file) {
        byte[] content = readFile(file);
        HierarchySnapshot hierarchy = loadHierarchy();
        ValidationCollector collector = new ValidationCollector();
        List<PreparedRow> preparedRows = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (!(workbook instanceof XSSFWorkbook)) {
                throw new BusinessException("只支持 .xlsx 格式，不支持旧版 .xls");
            }
            int headerRowIndex = validateWorkbookStructure(workbook);
            Sheet sheet = workbook.getSheet(INPUT_SHEET);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            Set<String> fileDuplicates = new HashSet<>();
            int effectiveRows = 0;

            for (int rowIndex = headerRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlank(row, formatter)) continue;
                if (isPlaceholder(row, formatter)) continue;
                effectiveRows++;
                int excelRow = rowIndex + 1;
                int beforeErrors = collector.errorCount();

                if (hasFormula(row)) {
                    collector.error(excelRow, "单元格", "第 " + excelRow + " 行：数据区不允许使用公式单元格");
                    continue;
                }
                QuestionSaveRequest request = parseRow(row, formatter, excelRow, hierarchy, collector);
                if (request == null || collector.errorCount() > beforeErrors) continue;

                QuestionValidationSupport.PreparedQuestion prepared;
                try {
                    prepared = QuestionValidationSupport.prepare(request);
                } catch (BusinessException exception) {
                    collector.error(excelRow, "题目", "第 " + excelRow + " 行：" + exception.getMessage());
                    continue;
                }

                String duplicateKey = duplicateKey(request.getKnowledgePointId(), request.getContent());
                if (!fileDuplicates.add(duplicateKey)) {
                    collector.duplicate(excelRow, "题干", "第 " + excelRow + " 行：文件内同一知识点下题干重复");
                    continue;
                }
                preparedRows.add(new PreparedRow(excelRow, request, prepared));
            }

            if (effectiveRows > MAX_QUESTION_ROWS) {
                collector.error(0, "文件", "有效题目超过 " + MAX_QUESTION_ROWS + " 道上限");
            }
            collector.setTotalRows(effectiveRows);
            validateDatabaseDuplicates(preparedRows, collector);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("无法解析 .xlsx 文件，请确认文件未损坏且来自系统模板");
        }

        Set<Integer> invalidRows = collector.invalidRowNumbers();
        List<PreparedRow> validRows = preparedRows.stream()
                .filter(row -> !invalidRows.contains(row.rowNumber())).toList();
        List<QuestionImportPreviewRow> previews = validRows.stream().limit(100)
                .map(row -> toPreview(row, hierarchy)).toList();
        QuestionImportValidationResult result = collector.result(validRows.size(), previews);
        return new ValidatedImport(result, validRows);
    }

    private byte[] readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("请选择非空的 .xlsx 文件");
        String filename = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (filename.endsWith(".xls")) throw new BusinessException("不支持旧版 .xls，请使用 .xlsx");
        if (!filename.endsWith(".xlsx")) throw new BusinessException("只接受 .xlsx 文件");
        if (file.getSize() > MAX_FILE_BYTES) throw new BusinessException("文件不能超过 5 MB");
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException("读取上传文件失败");
        }
    }

    private int validateWorkbookStructure(Workbook workbook) {
        if (workbook.getNumberOfSheets() != SHEET_NAMES.length) {
            throw new BusinessException("工作簿必须且只能包含：题目录入、填写示例、填写说明、知识点清单");
        }
        for (int index = 0; index < SHEET_NAMES.length; index++) {
            if (!SHEET_NAMES[index].equals(workbook.getSheetName(index))) {
                throw new BusinessException("工作表缺失、重命名或顺序错误，应依次为：" + String.join("、", SHEET_NAMES));
            }
        }
        Sheet inputSheet = workbook.getSheet(INPUT_SHEET);
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        if (isValidHeader(inputSheet.getRow(0), formatter)) return 0;
        if (isValidHeader(inputSheet.getRow(1), formatter)) return 1;
        throw new BusinessException("题目录入工作表第1行或第2行均不是合法的18列标准表头");
    }

    private boolean isValidHeader(Row header, DataFormatter formatter) {
        if (header == null) return false;
        for (int index = 0; index < HEADERS.length; index++) {
            if (!HEADERS[index].equals(value(header, index, formatter))) return false;
        }
        for (int index = HEADERS.length; index < header.getLastCellNum(); index++) {
            if (StringUtils.hasText(value(header, index, formatter))) return false;
        }
        return true;
    }

    private QuestionSaveRequest parseRow(Row row, DataFormatter formatter, int excelRow,
                                         HierarchySnapshot hierarchy, ValidationCollector collector) {
        String idText = value(row, 0, formatter);
        Long legacyKnowledgePointId = StringUtils.hasText(idText)
                ? parseId(idText, excelRow, collector) : null;
        HierarchySelection subjectSelection = parseHierarchySelection(
                value(row, 1, formatter), excelRow, "科目", collector);
        HierarchySelection chapterSelection = parseHierarchySelection(
                value(row, 2, formatter), excelRow, "章节", collector);
        HierarchySelection knowledgeSelection = parseHierarchySelection(
                value(row, 3, formatter), excelRow, "知识点", collector);
        Long selectedKnowledgePointId = knowledgeSelection != null && knowledgeSelection.structured()
                ? knowledgeSelection.id() : null;
        if (legacyKnowledgePointId != null && selectedKnowledgePointId != null
                && !legacyKnowledgePointId.equals(selectedKnowledgePointId)) {
            collector.error(excelRow, "知识点ID",
                    "第 " + excelRow + " 行：知识点ID与知识点下拉选项不一致");
        }
        Long knowledgePointId = legacyKnowledgePointId != null
                ? legacyKnowledgePointId : selectedKnowledgePointId;
        if (knowledgePointId == null && !StringUtils.hasText(idText)) {
            collector.error(excelRow, "知识点ID",
                    "第 " + excelRow + " 行：知识点ID不能为空，或请选择知识点");
        }
        HierarchyEntry entry = knowledgePointId == null ? null : hierarchy.byKnowledgePoint().get(knowledgePointId);
        if (knowledgePointId != null && entry == null) {
            collector.error(excelRow, "知识点ID", "第 " + excelRow + " 行：知识点ID不存在（知识点不存在）");
        }
        validateHierarchySelections(subjectSelection, chapterSelection, knowledgeSelection,
                entry, hierarchy, excelRow, collector);

        String type = TYPE_ALIASES.get(value(row, 4, formatter).toUpperCase(Locale.ROOT));
        if (type == null) collector.error(excelRow, "题型", "第 " + excelRow + " 行：题型不合法");
        Integer difficulty = DIFFICULTY_ALIASES.get(value(row, 5, formatter));
        if (difficulty == null) collector.error(excelRow, "难度", "第 " + excelRow + " 行：难度只能为简单/中等/困难或1/2/3");
        Integer status = STATUS_ALIASES.get(value(row, 6, formatter));
        if (status == null) collector.error(excelRow, "状态", "第 " + excelRow + " 行：状态只能为启用/禁用或1/0");
        String questionContent = value(row, 7, formatter);
        String answer = normalizeAnswer(type, value(row, 16, formatter));
        String analysis = value(row, 17, formatter);

        required(questionContent, excelRow, "题干", collector);
        required(answer, excelRow, "正确答案", collector);
        maxLength(questionContent, 10000, excelRow, "题干", collector);
        maxLength(answer, 255, excelRow, "正确答案", collector);
        maxLength(analysis, 10000, excelRow, "解析", collector);

        List<QuestionOptionRequest> options = new ArrayList<>();
        boolean gap = false;
        for (int index = 0; index < 8; index++) {
            String optionContent = value(row, 8 + index, formatter);
            if (!StringUtils.hasText(optionContent)) {
                gap = true;
                continue;
            }
            if (gap) collector.error(excelRow, "选项" + (char) ('A' + index),
                    "第 " + excelRow + " 行：选项必须从A开始连续填写，不允许中间断档");
            maxLength(optionContent, 5000, excelRow, "选项" + (char) ('A' + index), collector);
            QuestionOptionRequest option = new QuestionOptionRequest();
            option.setOptionKey(String.valueOf((char) ('A' + index)));
            option.setContent(optionContent);
            option.setSortOrder(index + 1);
            options.add(option);
        }

        if ("JUDGE".equals(type) && !options.isEmpty()) {
            collector.error(excelRow, "选项", "第 " + excelRow + " 行：判断题不能填写选项");
        }
        if (entry != null && (entry.subject().getStatus() == 0
                || entry.chapter().getStatus() == 0 || entry.knowledgePoint().getStatus() == 0)) {
            collector.warning(excelRow, "知识点ID", "第 " + excelRow + " 行：目标层级包含已禁用内容，导入后题目可能不可见");
        }

        QuestionSaveRequest request = new QuestionSaveRequest();
        request.setKnowledgePointId(knowledgePointId);
        request.setType(type);
        request.setContent(questionContent);
        request.setAnswer(answer);
        request.setAnalysis(analysis);
        request.setDifficulty(difficulty);
        request.setStatus(status);
        request.setOptions(options);
        return request;
    }

    private HierarchySelection parseHierarchySelection(String value, int row, String field,
                                                        ValidationCollector collector) {
        if (!StringUtils.hasText(value)) return null;
        int separatorIndex = value.indexOf(HIERARCHY_SEPARATOR);
        if (separatorIndex < 0) return new HierarchySelection(null, value, false);
        String idText = value.substring(0, separatorIndex).trim();
        String name = value.substring(separatorIndex + HIERARCHY_SEPARATOR.length()).trim();
        if (!StringUtils.hasText(idText) || !StringUtils.hasText(name)) {
            collector.error(row, field, "第 " + row + " 行：" + field + "选项格式不合法");
            return new HierarchySelection(null, name, true);
        }
        try {
            long id = Long.parseLong(idText);
            if (id <= 0) throw new NumberFormatException();
            return new HierarchySelection(id, name, true);
        } catch (NumberFormatException exception) {
            collector.error(row, field, "第 " + row + " 行：" + field + "选项格式不合法，ID必须为正整数");
            return new HierarchySelection(null, name, true);
        }
    }

    private void validateHierarchySelections(HierarchySelection subjectSelection,
                                             HierarchySelection chapterSelection,
                                             HierarchySelection knowledgeSelection,
                                             HierarchyEntry finalEntry,
                                             HierarchySnapshot hierarchy,
                                             int row,
                                             ValidationCollector collector) {
        Subject selectedSubject = validateSubjectSelection(subjectSelection, hierarchy, row, collector);
        Chapter selectedChapter = validateChapterSelection(chapterSelection, hierarchy, row, collector);
        HierarchyEntry selectedKnowledge = validateKnowledgeSelection(
                knowledgeSelection, hierarchy, row, collector);

        if (selectedSubject != null && selectedChapter != null
                && !selectedSubject.getId().equals(selectedChapter.getSubjectId())) {
            collector.error(row, "章节", "第 " + row + " 行：章节“" + selectedChapter.getName()
                    + "”不属于所选科目“" + selectedSubject.getName() + "”");
        }
        if (selectedChapter != null && selectedKnowledge != null
                && !selectedChapter.getId().equals(selectedKnowledge.knowledgePoint().getChapterId())) {
            collector.error(row, "知识点", "第 " + row + " 行：知识点“"
                    + selectedKnowledge.knowledgePoint().getName() + "”不属于所选章节“"
                    + selectedChapter.getName() + "”");
        }
        if (finalEntry == null) return;

        validateSelectionAgainstFinal(subjectSelection, finalEntry.subject().getId(),
                finalEntry.subject().getName(), row, "科目", collector);
        validateSelectionAgainstFinal(chapterSelection, finalEntry.chapter().getId(),
                finalEntry.chapter().getName(), row, "章节", collector);
        validateSelectionAgainstFinal(knowledgeSelection, finalEntry.knowledgePoint().getId(),
                finalEntry.knowledgePoint().getName(), row, "知识点", collector);
    }

    private Subject validateSubjectSelection(HierarchySelection selection, HierarchySnapshot hierarchy,
                                             int row, ValidationCollector collector) {
        if (selection == null || !selection.structured() || selection.id() == null) return null;
        Subject subject = hierarchy.subjects().get(selection.id());
        if (subject == null) {
            collector.error(row, "科目", "第 " + row + " 行：科目不存在");
        } else if (!selection.name().equals(subject.getName())) {
            collector.error(row, "科目", "第 " + row + " 行：科目名称与ID不一致");
        }
        return subject;
    }

    private Chapter validateChapterSelection(HierarchySelection selection, HierarchySnapshot hierarchy,
                                             int row, ValidationCollector collector) {
        if (selection == null || !selection.structured() || selection.id() == null) return null;
        Chapter chapter = hierarchy.chapters().get(selection.id());
        if (chapter == null) {
            collector.error(row, "章节", "第 " + row + " 行：章节不存在");
        } else if (!selection.name().equals(chapter.getName())) {
            collector.error(row, "章节", "第 " + row + " 行：章节名称与ID不一致");
        }
        return chapter;
    }

    private HierarchyEntry validateKnowledgeSelection(HierarchySelection selection,
                                                      HierarchySnapshot hierarchy,
                                                      int row, ValidationCollector collector) {
        if (selection == null || !selection.structured() || selection.id() == null) return null;
        HierarchyEntry entry = hierarchy.byKnowledgePoint().get(selection.id());
        if (entry == null) {
            collector.error(row, "知识点", "第 " + row + " 行：知识点不存在");
        } else if (!selection.name().equals(entry.knowledgePoint().getName())) {
            collector.error(row, "知识点", "第 " + row + " 行：知识点名称与ID不一致");
        }
        return entry;
    }

    private void validateSelectionAgainstFinal(HierarchySelection selection, Long expectedId,
                                               String expectedName, int row, String field,
                                               ValidationCollector collector) {
        if (selection == null) return;
        if (selection.structured()) {
            if (selection.id() != null && !selection.id().equals(expectedId)) {
                collector.error(row, field, "第 " + row + " 行：" + field + "与最终知识点层级不一致");
            }
        } else {
            validateName(selection.name(), expectedName, row, field, collector);
        }
    }

    private void validateDatabaseDuplicates(List<PreparedRow> rows, ValidationCollector collector) {
        Set<Long> ids = rows.stream().map(row -> row.request().getKnowledgePointId())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return;
        Set<String> existing = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                        .in(Question::getKnowledgePointId, ids))
                .stream().map(question -> duplicateKey(question.getKnowledgePointId(), question.getContent()))
                .collect(Collectors.toSet());
        for (PreparedRow row : rows) {
            if (existing.contains(duplicateKey(row.request().getKnowledgePointId(), row.request().getContent()))) {
                collector.duplicate(row.rowNumber(), "题干",
                        "第 " + row.rowNumber() + " 行：同一知识点下已存在相同题干");
            }
        }
    }

    private QuestionImportPreviewRow toPreview(PreparedRow row, HierarchySnapshot hierarchy) {
        HierarchyEntry entry = hierarchy.byKnowledgePoint().get(row.request().getKnowledgePointId());
        return new QuestionImportPreviewRow(row.rowNumber(), row.request().getKnowledgePointId(),
                entry.subject().getName(), entry.chapter().getName(), entry.knowledgePoint().getName(),
                row.prepared().type(), row.request().getDifficulty(), row.request().getStatus(),
                row.request().getContent(), row.prepared().answer(), row.prepared().options().size());
    }

    private HierarchySnapshot loadHierarchy() {
        Map<Long, Subject> subjects = subjectMapper.selectList(null).stream()
                .collect(Collectors.toMap(Subject::getId, Function.identity()));
        Map<Long, Chapter> chapters = chapterMapper.selectList(null).stream()
                .collect(Collectors.toMap(Chapter::getId, Function.identity()));
        List<HierarchyEntry> entries = knowledgePointMapper.selectList(null).stream()
                .map(knowledgePoint -> {
                    Chapter chapter = chapters.get(knowledgePoint.getChapterId());
                    Subject subject = chapter == null ? null : subjects.get(chapter.getSubjectId());
                    return chapter == null || subject == null ? null
                            : new HierarchyEntry(subject, chapter, knowledgePoint);
                })
                .filter(Objects::nonNull)
                .sorted((left, right) -> Long.compare(left.knowledgePoint().getId(), right.knowledgePoint().getId()))
                .toList();
        Map<Long, HierarchyEntry> byId = entries.stream().collect(Collectors.toMap(
                entry -> entry.knowledgePoint().getId(), Function.identity(), (left, right) -> left, LinkedHashMap::new));
        return new HierarchySnapshot(entries, byId, subjects, chapters);
    }

    private void createInputSheet(Workbook workbook, ExcelExportSupport.Styles styles) {
        Sheet sheet = workbook.createSheet(INPUT_SHEET);
        for (int column = 0; column < HEADERS.length; column++) {
            boolean longText = (column >= 7 && column <= 15) || column == 17;
            sheet.setDefaultColumnStyle(column, longText ? styles.wrap() : styles.text());
        }
        Row prompt = sheet.createRow(0);
        String promptText = "请从第 3 行开始填写，依次选择科目、章节和知识点；章节和知识点下拉框会随上级选项变化，题目ID和选项ID由系统自动生成。";
        for (int column = 0; column < HEADERS.length; column++) {
            ExcelExportSupport.writeText(prompt, column, column == 0 ? promptText : "", styles.prompt());
        }
        prompt.setHeightInPoints(32);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));
        ExcelExportSupport.writeHeader(sheet.createRow(INPUT_HEADER_ROW), HEADERS, styles);
        sheet.createFreezePane(0, INPUT_DATA_START_ROW);
        sheet.setAutoFilter(new CellRangeAddress(INPUT_HEADER_ROW,
                INPUT_DATA_START_ROW + MAX_QUESTION_ROWS - 1, 0, HEADERS.length - 1));
        ExcelExportSupport.setColumnWidths(sheet,
                13, 16, 20, 22, 13, 11, 11, 46, 26, 26, 26, 26, 26, 26, 26, 26, 16, 44);
        sheet.setColumnHidden(0, true);
        addDropdown(sheet, 4, new String[]{"单选题", "多选题", "判断题", "SINGLE", "MULTIPLE", "JUDGE"});
        addDropdown(sheet, 5, new String[]{"简单", "中等", "困难", "1", "2", "3"});
        addDropdown(sheet, 6, new String[]{"启用", "禁用", "1", "0"});
    }

    private void createExampleSheet(Workbook workbook, ExcelExportSupport.Styles styles,
                                    HierarchySnapshot hierarchy) {
        Sheet sheet = workbook.createSheet("填写示例");
        ExcelExportSupport.writeHeader(sheet.createRow(0), HEADERS, styles);
        HierarchyEntry entry = hierarchy.entries().stream().filter(this::isActive).findFirst().orElse(null);
        writeExample(sheet.createRow(1), styles, entry, "单选题", "中等", "启用",
                "示例：下列哪项描述正确？", List.of("选项一", "选项二"), "A", "单选题示例解析");
        writeExample(sheet.createRow(2), styles, entry, "多选题", "简单", "启用",
                "示例：请选择所有正确选项。", List.of("选项一", "选项二", "选项三"), "A,C", "多选题示例解析");
        writeExample(sheet.createRow(3), styles, entry, "判断题", "困难", "启用",
                "示例：该陈述是否正确？", List.of(), "TRUE", "判断题示例解析");
        sheet.createFreezePane(0, 1);
        ExcelExportSupport.setColumnWidths(sheet,
                13, 16, 20, 22, 13, 11, 11, 46, 26, 26, 26, 26, 26, 26, 26, 26, 16, 44);
    }

    private void createInstructionSheet(Workbook workbook, ExcelExportSupport.Styles styles,
                                        boolean emptyHierarchy) {
        Sheet sheet = workbook.createSheet("填写说明");
        ExcelExportSupport.titleRow(sheet, "题库批量导入填写说明", 1, styles);
        String[] instructions = {
                "仅可修改并上传 .xlsx 文件；系统只读取“题目录入”工作表，不会导入“填写示例”或“知识点清单”。",
                "请从“题目录入”第3行开始填写，每行代表一道题；同一知识点可以重复填写多行。",
                "新模板无需填写已隐藏的知识点ID列；请依次选择科目、章节、知识点，下拉选项采用“ID｜名称”格式，系统从知识点选项取得ID。",
                "章节下拉框随科目变化，知识点下拉框随章节变化；修改父级后请重新选择下级，无宏模板不会自动清除原下级值。",
                "系统上传预校验会按当前数据库重新核对科目、章节、知识点和ID，层级组合不一致时会拒绝该行；旧模板仍可直接填写知识点ID导入。",
                "必填：题型、难度、状态、题干、正确答案，以及可确定知识点ID的知识点选项或旧模板知识点ID。",
                "题型：单选题/SINGLE、多选题/MULTIPLE、判断题/JUDGE；难度：简单/1、中等/2、困难/3；状态：启用/1、禁用/0。",
                "选择题至少两个选项，选项从A开始连续填写，本模板最多支持A-H；单选答案如A，多选答案如A,C。",
                "判断题答案可填TRUE/FALSE或正确/错误，且选项A-H必须全部留空；解析可以为空。",
                "题目ID和选项ID由数据库自动生成，Excel不得指定；createdBy取当前登录管理员。",
                "可在“知识点清单”核对完整层级及启用状态；旧模板中的纯名称仍可留空或用于核对，但填写后必须与知识点ID完全对应。",
                "同一知识点下相同题干（trim后、区分大小写）视为重复，文件内或数据库已有重复都会使整次导入失败。",
                "真正空行以及仅包含系统预填层级、没有题目业务字段的占位行会被忽略。",
                "单元格不得使用公式；单文件不超过5 MB，最多1000道有效题目。",
                "数学公式请以普通文本填写，网页由KaTeX渲染；行内公式示例：\\(2^6=64\\) 或 $2^6=64$。",
                "块级公式示例：\\[a^2+b^2=c^2\\] 或 $$a^2+b^2=c^2$$；不带分隔符的2^6会按普通文本显示。",
                emptyHierarchy ? "当前数据库没有可用知识点，请先创建科目、章节和知识点后再填写。" : "知识点清单由当前数据库动态生成。"
        };
        for (int index = 0; index < instructions.length; index++) {
            Row row = sheet.createRow(index + 2);
            ExcelExportSupport.writeNumber(row, 0, index + 1, styles.text());
            ExcelExportSupport.writeText(row, 1, instructions[index], styles.wrap());
            row.setHeightInPoints(36);
        }
        ExcelExportSupport.setColumnWidths(sheet, 8, 100);
    }

    private void createKnowledgeSheet(Workbook workbook, ExcelExportSupport.Styles styles,
                                      HierarchySnapshot hierarchy) {
        Sheet sheet = workbook.createSheet("知识点清单");
        String[] headers = {"知识点ID", "科目", "章节", "知识点", "科目状态", "章节状态", "知识点状态"};
        ExcelExportSupport.writeHeader(sheet.createRow(0), headers, styles);
        int rowIndex = 1;
        for (HierarchyEntry entry : orderedKnowledgeEntries(hierarchy)) {
            Row row = sheet.createRow(rowIndex++);
            ExcelExportSupport.writeNumber(row, 0, entry.knowledgePoint().getId(), styles.text());
            ExcelExportSupport.writeText(row, 1, entry.subject().getName(), styles.text());
            ExcelExportSupport.writeText(row, 2, entry.chapter().getName(), styles.text());
            ExcelExportSupport.writeText(row, 3, entry.knowledgePoint().getName(), styles.text());
            ExcelExportSupport.writeText(row, 4, statusLabel(entry.subject().getStatus()), styles.text());
            ExcelExportSupport.writeText(row, 5, statusLabel(entry.chapter().getStatus()), styles.text());
            ExcelExportSupport.writeText(row, 6, statusLabel(entry.knowledgePoint().getStatus()), styles.text());
        }
        if (hierarchy.entries().isEmpty()) {
            ExcelExportSupport.writeText(sheet.createRow(1), 0, "当前暂无知识点，请先维护课程层级", styles.wrap());
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, headers.length - 1));
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(1, rowIndex - 1), 0, headers.length - 1));
        ExcelExportSupport.setColumnWidths(sheet, 13, 18, 22, 24, 13, 13, 13);
    }

    private void writeExample(Row row, ExcelExportSupport.Styles styles, HierarchyEntry entry,
                              String type, String difficulty, String status, String content,
                              List<String> options, String answer, String analysis) {
        if (entry != null) {
            ExcelExportSupport.writeText(row, 1, hierarchyOption(
                    entry.subject().getId(), entry.subject().getName()), styles.text());
            ExcelExportSupport.writeText(row, 2, hierarchyOption(
                    entry.chapter().getId(), entry.chapter().getName()), styles.text());
            ExcelExportSupport.writeText(row, 3, hierarchyOption(
                    entry.knowledgePoint().getId(), entry.knowledgePoint().getName()), styles.text());
        }
        ExcelExportSupport.writeText(row, 4, type, styles.text());
        ExcelExportSupport.writeText(row, 5, difficulty, styles.text());
        ExcelExportSupport.writeText(row, 6, status, styles.text());
        ExcelExportSupport.writeText(row, 7, content, styles.wrap());
        for (int index = 0; index < options.size(); index++) {
            ExcelExportSupport.writeText(row, 8 + index, options.get(index), styles.wrap());
        }
        ExcelExportSupport.writeText(row, 16, answer, styles.text());
        ExcelExportSupport.writeText(row, 17, analysis, styles.wrap());
    }

    private void addHierarchyDropdowns(Workbook workbook, Sheet inputSheet, Sheet knowledgeSheet,
                                       ExcelExportSupport.Styles styles, HierarchySnapshot hierarchy) {
        List<HierarchyEntry> activeEntries = hierarchy.entries().stream()
                .filter(this::isActive)
                .sorted(Comparator.comparing((HierarchyEntry entry) -> entry.subject().getId())
                        .thenComparing(entry -> entry.chapter().getId())
                        .thenComparing(entry -> entry.knowledgePoint().getId()))
                .toList();
        if (activeEntries.isEmpty()) return;

        Map<Long, Subject> subjects = new LinkedHashMap<>();
        Map<Long, LinkedHashMap<Long, Chapter>> chaptersBySubject = new LinkedHashMap<>();
        Map<Long, List<KnowledgePoint>> knowledgeByChapter = new LinkedHashMap<>();
        for (HierarchyEntry entry : activeEntries) {
            long subjectId = entry.subject().getId();
            long chapterId = entry.chapter().getId();
            subjects.putIfAbsent(subjectId, entry.subject());
            chaptersBySubject.computeIfAbsent(subjectId, ignored -> new LinkedHashMap<>())
                    .putIfAbsent(chapterId, entry.chapter());
            knowledgeByChapter.computeIfAbsent(chapterId, ignored -> new ArrayList<>())
                    .add(entry.knowledgePoint());
        }

        int helperColumn = HELPER_START_COLUMN;
        createNamedHelperColumn(workbook, knowledgeSheet, styles, helperColumn++,
                VALID_SUBJECTS_NAME, subjects.values().stream()
                        .map(subject -> hierarchyOption(subject.getId(), subject.getName())).toList());
        createNamedHelperColumn(workbook, knowledgeSheet, styles, helperColumn++,
                EMPTY_HIERARCHY_OPTIONS_NAME, List.of(""));
        for (Map.Entry<Long, LinkedHashMap<Long, Chapter>> subjectChapters : chaptersBySubject.entrySet()) {
            createNamedHelperColumn(workbook, knowledgeSheet, styles, helperColumn++,
                    CHAPTERS_NAME_PREFIX + subjectChapters.getKey(),
                    subjectChapters.getValue().values().stream()
                            .map(chapter -> hierarchyOption(chapter.getId(), chapter.getName())).toList());
        }
        for (Map.Entry<Long, List<KnowledgePoint>> chapterKnowledge : knowledgeByChapter.entrySet()) {
            createNamedHelperColumn(workbook, knowledgeSheet, styles, helperColumn++,
                    KNOWLEDGE_NAME_PREFIX + chapterKnowledge.getKey(),
                    chapterKnowledge.getValue().stream()
                            .map(knowledge -> hierarchyOption(knowledge.getId(), knowledge.getName())).toList());
        }

        addFormulaDropdown(inputSheet, 1, VALID_SUBJECTS_NAME);
        addFormulaDropdown(inputSheet, 2,
                "INDIRECT(IF($B3=\"\",\"" + EMPTY_HIERARCHY_OPTIONS_NAME + "\",\""
                        + CHAPTERS_NAME_PREFIX + "\"&LEFT($B3,FIND(\"" + HIERARCHY_SEPARATOR
                        + "\",$B3)-1)))");
        addFormulaDropdown(inputSheet, 3,
                "INDIRECT(IF($C3=\"\",\"" + EMPTY_HIERARCHY_OPTIONS_NAME + "\",\""
                        + KNOWLEDGE_NAME_PREFIX + "\"&LEFT($C3,FIND(\"" + HIERARCHY_SEPARATOR
                        + "\",$C3)-1)))");
    }

    private void createNamedHelperColumn(Workbook workbook, Sheet sheet,
                                         ExcelExportSupport.Styles styles, int column,
                                         String nameText, List<String> values) {
        Row header = sheet.getRow(0);
        ExcelExportSupport.writeText(header, column, nameText, styles.header());
        for (int index = 0; index < values.size(); index++) {
            Row row = sheet.getRow(index + 1);
            if (row == null) row = sheet.createRow(index + 1);
            ExcelExportSupport.writeText(row, column, values.get(index), styles.text());
        }
        String columnName = CellReference.convertNumToColString(column);
        Name name = workbook.createName();
        name.setNameName(nameText);
        name.setRefersToFormula("'知识点清单'!$" + columnName + "$2:$" + columnName
                + "$" + (values.size() + 1));
        sheet.setColumnHidden(column, true);
    }

    private String hierarchyOption(Long id, String name) {
        return id + HIERARCHY_SEPARATOR + name;
    }

    private List<HierarchyEntry> orderedKnowledgeEntries(HierarchySnapshot hierarchy) {
        List<HierarchyEntry> ordered = new ArrayList<>();
        ordered.addAll(hierarchy.entries().stream().filter(this::isActive).toList());
        ordered.addAll(hierarchy.entries().stream().filter(entry -> !isActive(entry)).toList());
        return ordered;
    }

    private boolean isActive(HierarchyEntry entry) {
        return entry.subject().getStatus() == 1 && entry.chapter().getStatus() == 1
                && entry.knowledgePoint().getStatus() == 1;
    }

    private void addDropdown(Sheet sheet, int column, String[] values) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(values);
        addValidation(sheet, helper, constraint, column);
    }

    private void addFormulaDropdown(Sheet sheet, int column, String formula) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createFormulaListConstraint(formula);
        addValidation(sheet, helper, constraint, column);
    }

    private void addValidation(Sheet sheet, DataValidationHelper helper,
                               DataValidationConstraint constraint, int column) {
        DataValidation validation = helper.createValidation(constraint,
                new CellRangeAddressList(INPUT_DATA_START_ROW,
                        INPUT_DATA_START_ROW + MAX_QUESTION_ROWS - 1, column, column));
        validation.setShowErrorBox(true);
        validation.createErrorBox("输入不合法", "请从下拉列表选择允许值");
        validation.setShowPromptBox(false);
        sheet.addValidationData(validation);
    }

    private boolean hasFormula(Row row) {
        for (int column = 0; column < HEADERS.length; column++) {
            Cell cell = row.getCell(column);
            if (cell != null && cell.getCellType() == CellType.FORMULA) return true;
        }
        return false;
    }

    private boolean isBlank(Row row, DataFormatter formatter) {
        for (int column = 0; column < HEADERS.length; column++) {
            if (StringUtils.hasText(value(row, column, formatter))) return false;
        }
        return true;
    }

    private boolean isPlaceholder(Row row, DataFormatter formatter) {
        for (int column = 4; column < HEADERS.length; column++) {
            if (StringUtils.hasText(value(row, column, formatter))) return false;
        }
        return true;
    }

    private static String value(Row row, int column, DataFormatter formatter) {
        Cell cell = row == null ? null : row.getCell(column);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private Long parseId(String value, int row, ValidationCollector collector) {
        if (!StringUtils.hasText(value)) {
            collector.error(row, "知识点ID", "第 " + row + " 行：知识点ID不能为空");
            return null;
        }
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            collector.error(row, "知识点ID", "第 " + row + " 行：知识点ID必须为正整数");
            return null;
        }
    }

    private void validateName(String actual, String expected, int row, String field,
                              ValidationCollector collector) {
        if (StringUtils.hasText(actual) && expected != null && !actual.equals(expected)) {
            collector.error(row, field, "第 " + row + " 行：" + field + "与知识点ID对应层级不一致");
        }
    }

    private void required(String value, int row, String field, ValidationCollector collector) {
        if (!StringUtils.hasText(value)) collector.error(row, field, "第 " + row + " 行：" + field + "不能为空");
    }

    private void maxLength(String value, int max, int row, String field, ValidationCollector collector) {
        if (value != null && value.length() > max) {
            collector.error(row, field, "第 " + row + " 行：" + field + "不能超过" + max + "个字符");
        }
    }

    private String normalizeAnswer(String type, String answer) {
        String normalized = answer.trim().toUpperCase(Locale.ROOT)
                .replace('，', ',');
        if ("JUDGE".equals(type)) {
            if ("正确".equals(answer.trim())) return "TRUE";
            if ("错误".equals(answer.trim())) return "FALSE";
        }
        return List.of(normalized.split(",")).stream()
                .map(String::trim).filter(StringUtils::hasText)
                .map(value -> value.toUpperCase(Locale.ROOT)).distinct().sorted()
                .collect(Collectors.joining(","));
    }

    private String duplicateKey(Long knowledgePointId, String content) {
        return knowledgePointId + "\u0000" + (content == null ? "" : content.trim());
    }

    private String statusLabel(Integer status) {
        return Integer.valueOf(1).equals(status) ? "启用" : "禁用";
    }

    private record HierarchyEntry(Subject subject, Chapter chapter, KnowledgePoint knowledgePoint) {
    }

    private record HierarchySelection(Long id, String name, boolean structured) {
    }

    private record HierarchySnapshot(List<HierarchyEntry> entries,
                                     Map<Long, HierarchyEntry> byKnowledgePoint,
                                     Map<Long, Subject> subjects,
                                     Map<Long, Chapter> chapters) {
    }

    private record PreparedRow(int rowNumber, QuestionSaveRequest request,
                               QuestionValidationSupport.PreparedQuestion prepared) {
    }

    private record ValidatedImport(QuestionImportValidationResult result, List<PreparedRow> rows) {
    }

    private static final class ValidationCollector {
        private final List<QuestionImportIssue> errors = new ArrayList<>();
        private final List<QuestionImportIssue> warnings = new ArrayList<>();
        private final Set<Integer> invalidRows = new LinkedHashSet<>();
        private final Set<Integer> duplicateRows = new LinkedHashSet<>();
        private int totalErrors;
        private int totalWarnings;
        private int totalRows;

        void error(int row, String field, String message) {
            totalErrors++;
            if (row > 0) invalidRows.add(row);
            if (errors.size() < MAX_RETURNED_ISSUES) errors.add(new QuestionImportIssue(row, field, message));
        }

        void duplicate(int row, String field, String message) {
            duplicateRows.add(row);
            error(row, field, message);
        }

        void warning(int row, String field, String message) {
            totalWarnings++;
            if (warnings.size() < MAX_RETURNED_ISSUES) warnings.add(new QuestionImportIssue(row, field, message));
        }

        int errorCount() { return totalErrors; }
        void setTotalRows(int value) { totalRows = value; }
        Set<Integer> invalidRowNumbers() { return invalidRows; }

        QuestionImportValidationResult result(int validRows, List<QuestionImportPreviewRow> previews) {
            return new QuestionImportValidationResult(totalErrors == 0, totalRows, validRows,
                    invalidRows.size(), duplicateRows.size(), totalWarnings,
                    Math.max(0, totalErrors - errors.size()), List.copyOf(errors),
                    List.copyOf(warnings), previews);
        }
    }
}
