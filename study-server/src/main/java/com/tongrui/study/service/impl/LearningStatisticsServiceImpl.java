package com.tongrui.study.service.impl;

import com.tongrui.study.mapper.LearningStatisticsMapper;
import com.tongrui.study.service.LearningStatisticsService;
import com.tongrui.study.vo.DailyStatisticsVO;
import com.tongrui.study.vo.KnowledgePointStatisticsVO;
import com.tongrui.study.vo.LearningStatisticsVO;
import com.tongrui.study.vo.StatisticsSummaryVO;
import com.tongrui.study.vo.SubjectStatisticsVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class LearningStatisticsServiceImpl implements LearningStatisticsService {

    private final LearningStatisticsMapper learningStatisticsMapper;

    public LearningStatisticsServiceImpl(LearningStatisticsMapper learningStatisticsMapper) {
        this.learningStatisticsMapper = learningStatisticsMapper;
    }

    @Override
    public LearningStatisticsVO overview(Long userId, int days) {
        StatisticsSummaryVO summary = learningStatisticsMapper.selectSummary(userId);
        List<SubjectStatisticsVO> subjectStatistics = learningStatisticsMapper.selectSubjectStatistics(userId);
        List<KnowledgePointStatisticsVO> knowledgePointStatistics =
                learningStatisticsMapper.selectKnowledgePointStatistics(userId);

        List<KnowledgePointStatisticsVO> weakKnowledgePoints = knowledgePointStatistics.stream()
                .filter(item -> item.getAnswerCount() >= 2)
                .sorted(Comparator.comparing(KnowledgePointStatisticsVO::getAccuracyRate)
                        .thenComparing(Comparator.comparingLong(
                                KnowledgePointStatisticsVO::getAnswerCount).reversed())
                        .thenComparing(KnowledgePointStatisticsVO::getKnowledgePointId))
                .limit(5)
                .toList();

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);
        Map<LocalDate, DailyStatisticsVO> dailyByDate = learningStatisticsMapper
                .selectDailyStatistics(userId, startDate, endDate)
                .stream().collect(Collectors.toMap(DailyStatisticsVO::getDate, Function.identity()));
        List<DailyStatisticsVO> dailyTrend = IntStream.range(0, days)
                .mapToObj(offset -> {
                    LocalDate date = startDate.plusDays(offset);
                    return dailyByDate.getOrDefault(date,
                            new DailyStatisticsVO(date, 0L, 0L, 0L, BigDecimal.ZERO));
                })
                .toList();

        return new LearningStatisticsVO(summary, subjectStatistics, knowledgePointStatistics,
                weakKnowledgePoints, dailyTrend);
    }
}
