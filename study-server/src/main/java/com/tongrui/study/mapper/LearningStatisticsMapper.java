package com.tongrui.study.mapper;

import com.tongrui.study.vo.DailyStatisticsVO;
import com.tongrui.study.vo.KnowledgePointStatisticsVO;
import com.tongrui.study.vo.StatisticsSummaryVO;
import com.tongrui.study.vo.SubjectStatisticsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface LearningStatisticsMapper {

    @Select("""
            SELECT COUNT(*) AS total_answer_count,
                   COALESCE(SUM(CASE WHEN ar.is_correct = 1 THEN 1 ELSE 0 END), 0) AS correct_answer_count,
                   COALESCE(SUM(CASE WHEN ar.is_correct = 0 THEN 1 ELSE 0 END), 0) AS incorrect_answer_count,
                   CASE WHEN COUNT(*) = 0 THEN 0
                        ELSE ROUND(SUM(CASE WHEN ar.is_correct = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2)
                   END AS accuracy_rate,
                   COUNT(DISTINCT ar.question_id) AS answered_question_count,
                   (SELECT COUNT(*) FROM wrong_question w WHERE w.user_id = #{userId}) AS wrong_question_count,
                   (SELECT COUNT(*) FROM favorite f WHERE f.user_id = #{userId}) AS favorite_count,
                   COUNT(DISTINCT DATE(ar.answered_at)) AS study_days
            FROM answer_record ar
            WHERE ar.user_id = #{userId}
            """)
    StatisticsSummaryVO selectSummary(@Param("userId") Long userId);

    @Select("""
            SELECT s.id AS subject_id,
                   s.name AS subject_name,
                   COUNT(*) AS answer_count,
                   SUM(CASE WHEN ar.is_correct = 1 THEN 1 ELSE 0 END) AS correct_count,
                   SUM(CASE WHEN ar.is_correct = 0 THEN 1 ELSE 0 END) AS incorrect_count,
                   ROUND(SUM(CASE WHEN ar.is_correct = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2)
                       AS accuracy_rate
            FROM answer_record ar
            JOIN question q ON q.id = ar.question_id
            JOIN knowledge_point kp ON kp.id = q.knowledge_point_id
            JOIN chapter c ON c.id = kp.chapter_id
            JOIN subject s ON s.id = c.subject_id
            WHERE ar.user_id = #{userId}
            GROUP BY s.id, s.name
            ORDER BY answer_count DESC, s.id ASC
            """)
    List<SubjectStatisticsVO> selectSubjectStatistics(@Param("userId") Long userId);

    @Select("""
            SELECT s.id AS subject_id,
                   s.name AS subject_name,
                   c.id AS chapter_id,
                   c.name AS chapter_name,
                   kp.id AS knowledge_point_id,
                   kp.name AS knowledge_point_name,
                   COUNT(*) AS answer_count,
                   SUM(CASE WHEN ar.is_correct = 1 THEN 1 ELSE 0 END) AS correct_count,
                   SUM(CASE WHEN ar.is_correct = 0 THEN 1 ELSE 0 END) AS incorrect_count,
                   ROUND(SUM(CASE WHEN ar.is_correct = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2)
                       AS accuracy_rate
            FROM answer_record ar
            JOIN question q ON q.id = ar.question_id
            JOIN knowledge_point kp ON kp.id = q.knowledge_point_id
            JOIN chapter c ON c.id = kp.chapter_id
            JOIN subject s ON s.id = c.subject_id
            WHERE ar.user_id = #{userId}
            GROUP BY s.id, s.name, c.id, c.name, kp.id, kp.name
            ORDER BY answer_count DESC, kp.id ASC
            """)
    List<KnowledgePointStatisticsVO> selectKnowledgePointStatistics(@Param("userId") Long userId);

    @Select("""
            SELECT DATE(ar.answered_at) AS date,
                   COUNT(*) AS answer_count,
                   SUM(CASE WHEN ar.is_correct = 1 THEN 1 ELSE 0 END) AS correct_count,
                   SUM(CASE WHEN ar.is_correct = 0 THEN 1 ELSE 0 END) AS incorrect_count,
                   ROUND(SUM(CASE WHEN ar.is_correct = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2)
                       AS accuracy_rate
            FROM answer_record ar
            WHERE ar.user_id = #{userId}
              AND ar.answered_at >= #{startDate}
              AND ar.answered_at < DATE_ADD(#{endDate}, INTERVAL 1 DAY)
            GROUP BY DATE(ar.answered_at)
            ORDER BY date ASC
            """)
    List<DailyStatisticsVO> selectDailyStatistics(@Param("userId") Long userId,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);
}
