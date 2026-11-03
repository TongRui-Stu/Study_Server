package com.tongrui.study.service;

import com.tongrui.study.vo.LearningStatisticsVO;

public interface LearningStatisticsService {

    LearningStatisticsVO overview(Long userId, int days);
}
