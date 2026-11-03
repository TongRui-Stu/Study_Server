package com.tongrui.study.service;

import com.tongrui.study.dto.QuestionExportFilter;
import com.tongrui.study.entity.User;
import com.tongrui.study.mapper.UserMapper;
import com.tongrui.study.util.ExcelExportSupport;
import com.tongrui.study.vo.ExcelExportFile;
import com.tongrui.study.vo.QuestionOptionVO;
import com.tongrui.study.vo.QuestionVO;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuestionExcelExportService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "SINGLE", "单选题", "MULTIPLE", "多选题", "JUDGE", "判断题");
    private static final Map<Integer, String> DIFFICULTY_LABELS = Map.of(1, "简单", 2, "中等", 3, "困难");

    private final QuestionService questionService;
    private final UserMapper userMapper;

    public QuestionExcelExportService(QuestionService questionService, UserMapper userMapper) {
        this.questionService = questionService;
        this.userMapper = userMapper;
    }

    public ExcelExportFile export(QuestionExportFilter filter) {
        List<QuestionVO> questions = questionService.listForExport(filter);
        Map<Long, User> creators = loadCreators(questions);
        LocalDateTime exportedAt = LocalDateTime.now();

        Workbook workbook = new XSSFWorkbook();
        ExcelExportSupport.Styles styles = ExcelExportSupport.createStyles(workbook);
        Sheet sheet = workbook.createSheet("题库");
        ExcelExportSupport.titleRow(sheet, "管理员题库导出", 13, styles);

        Row exported = sheet.createRow(1);
        ExcelExportSupport.writeText(exported, 0, "导出时间", styles.note());
        ExcelExportSupport.writeText(exported, 1, ExcelExportSupport.formatDateTime(exportedAt), styles.note());
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 1, 13));
        Row scope = sheet.createRow(2);
        ExcelExportSupport.writeText(scope, 0, "筛选范围", styles.note());
        ExcelExportSupport.writeText(scope, 1, filterDescription(filter), styles.note());
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 1, 13));

        Row header = sheet.createRow(3);
        ExcelExportSupport.writeHeader(header, new String[]{
                "题目ID", "科目", "章节", "知识点", "题型", "难度", "状态",
                "题干", "选项", "正确答案", "解析", "创建人", "创建时间", "更新时间"
        }, styles);

        int rowIndex = 4;
        for (QuestionVO question : questions) {
            Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(42);
            ExcelExportSupport.writeNumber(row, 0, question.getId(), styles.text());
            ExcelExportSupport.writeText(row, 1, question.getSubjectName(), styles.wrap());
            ExcelExportSupport.writeText(row, 2, question.getChapterName(), styles.wrap());
            ExcelExportSupport.writeText(row, 3, question.getKnowledgePointName(), styles.wrap());
            ExcelExportSupport.writeText(row, 4,
                    TYPE_LABELS.getOrDefault(question.getType(), question.getType()), styles.text());
            ExcelExportSupport.writeText(row, 5,
                    DIFFICULTY_LABELS.getOrDefault(question.getDifficulty(), ""), styles.text());
            ExcelExportSupport.writeText(row, 6,
                    Integer.valueOf(1).equals(question.getStatus()) ? "启用" : "禁用", styles.text());
            ExcelExportSupport.writeText(row, 7, question.getContent(), styles.wrap());
            ExcelExportSupport.writeText(row, 8, optionText(question.getOptions()), styles.wrap());
            ExcelExportSupport.writeText(row, 9, question.getAnswer(), styles.wrap());
            ExcelExportSupport.writeText(row, 10, question.getAnalysis(), styles.wrap());
            ExcelExportSupport.writeText(row, 11, creatorName(question.getCreatedBy(), creators), styles.wrap());
            ExcelExportSupport.writeText(row, 12,
                    ExcelExportSupport.formatDateTime(question.getCreatedAt()), styles.text());
            ExcelExportSupport.writeText(row, 13,
                    ExcelExportSupport.formatDateTime(question.getUpdatedAt()), styles.text());
        }
        if (questions.isEmpty()) {
            Row row = sheet.createRow(rowIndex++);
            ExcelExportSupport.writeText(row, 0, "当前筛选条件下暂无题目", styles.wrap());
            sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, 13));
        }
        sheet.createFreezePane(0, 4);
        sheet.setAutoFilter(new CellRangeAddress(3, Math.max(3, rowIndex - 1), 0, 13));
        ExcelExportSupport.setColumnWidths(sheet,
                11, 16, 20, 20, 11, 10, 10, 44, 46, 15, 42, 18, 21, 21);

        String filename = "题库导出_" + exportedAt.format(FILE_TIME) + ".xlsx";
        return new ExcelExportFile(filename, ExcelExportSupport.toBytes(workbook));
    }

    private Map<Long, User> loadCreators(List<QuestionVO> questions) {
        Set<Long> ids = questions.stream().map(QuestionVO::getCreatedBy)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private String creatorName(Long id, Map<Long, User> creators) {
        if (id == null) {
            return "";
        }
        User user = creators.get(id);
        if (user == null) {
            return String.valueOf(id);
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private String optionText(List<QuestionOptionVO> options) {
        if (options == null || options.isEmpty()) {
            return "";
        }
        return options.stream()
                .sorted(Comparator.comparing(QuestionOptionVO::getSortOrder)
                        .thenComparing(QuestionOptionVO::getOptionKey))
                .map(option -> option.getOptionKey() + ". " + option.getContent())
                .collect(Collectors.joining("\n"));
    }

    private String filterDescription(QuestionExportFilter filter) {
        return "subjectId=" + value(filter.subjectId())
                + "，chapterId=" + value(filter.chapterId())
                + "，knowledgePointId=" + value(filter.knowledgePointId())
                + "，type=" + value(filter.type())
                + "，difficulty=" + value(filter.difficulty())
                + "，status=" + value(filter.status())
                + "，keyword=" + value(filter.keyword());
    }

    private String value(Object value) {
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? "全部" : String.valueOf(value);
    }
}
