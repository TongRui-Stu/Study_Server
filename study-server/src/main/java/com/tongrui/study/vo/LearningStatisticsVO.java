package com.tongrui.study.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LearningStatisticsVO {
    private StatisticsSummaryVO summary;
    private List<SubjectStatisticsVO> subjectStatistics;
    private List<KnowledgePointStatisticsVO> knowledgePointStatistics;
    private List<KnowledgePointStatisticsVO> weakKnowledgePoints;
    private List<DailyStatisticsVO> dailyTrend;
}
