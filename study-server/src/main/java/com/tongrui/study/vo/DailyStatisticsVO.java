package com.tongrui.study.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyStatisticsVO {
    private LocalDate date;
    private Long answerCount;
    private Long correctCount;
    private Long incorrectCount;
    private BigDecimal accuracyRate;
}
