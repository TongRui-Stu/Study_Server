package com.tongrui.study.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tongrui.study.dto.PersonalQuestionFilter;
import com.tongrui.study.entity.Question;
import com.tongrui.study.entity.WrongQuestion;
import com.tongrui.study.vo.WrongQuestionVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WrongQuestionMapper extends BaseMapper<WrongQuestion> {

    String PERSONAL_FILTER_AND_ORDER = """
            <if test="filter.subjectId != null">AND s.id = #{filter.subjectId}</if>
            <if test="filter.chapterId != null">AND c.id = #{filter.chapterId}</if>
            <if test="filter.knowledgePointId != null">AND kp.id = #{filter.knowledgePointId}</if>
            <if test="filter.type != null and filter.type != ''">AND q.type = #{filter.type}</if>
            <if test="filter.difficulty != null">AND q.difficulty = #{filter.difficulty}</if>
            <if test="filter.keyword != null and filter.keyword != ''">AND q.content LIKE CONCAT('%', #{filter.keyword}, '%')</if>
            <choose>
              <when test="filter.wrongCountOrder == 'DESC'">
                ORDER BY w.wrong_count DESC, w.last_wrong_at DESC, w.id DESC
              </when>
              <when test="filter.wrongCountOrder == 'ASC'">
                ORDER BY w.wrong_count ASC, w.last_wrong_at DESC, w.id DESC
              </when>
              <otherwise>
                ORDER BY w.last_wrong_at DESC, w.id DESC
              </otherwise>
            </choose>
            """;

    @Insert("""
            INSERT INTO wrong_question (user_id, question_id, wrong_count, last_wrong_at, created_at)
            VALUES (#{userId}, #{questionId}, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                wrong_count = wrong_count + 1,
                last_wrong_at = CURRENT_TIMESTAMP
            """)
    int upsertWrong(@Param("userId") Long userId, @Param("questionId") Long questionId);

    @Delete("""
            DELETE FROM wrong_question
            WHERE user_id = #{userId} AND question_id = #{questionId}
            """)
    int deleteByUserAndQuestion(@Param("userId") Long userId, @Param("questionId") Long questionId);

    @Select("""
            <script>
            SELECT q.id AS question_id,
                   q.type,
                   q.content,
                   q.difficulty,
                   q.answer AS correct_answer,
                   q.analysis,
                   s.id AS subject_id,
                   s.name AS subject_name,
                   c.id AS chapter_id,
                   c.name AS chapter_name,
                   kp.id AS knowledge_point_id,
                   kp.name AS knowledge_point_name,
                   w.wrong_count,
                   w.last_wrong_at,
                   CASE WHEN q.status = 1 AND kp.status = 1 AND c.status = 1 AND s.status = 1
                        THEN 1 ELSE 0 END AS question_status
            FROM wrong_question w
            JOIN question q ON q.id = w.question_id
            JOIN knowledge_point kp ON kp.id = q.knowledge_point_id
            JOIN chapter c ON c.id = kp.chapter_id
            JOIN subject s ON s.id = c.subject_id
            WHERE w.user_id = #{userId}
            """ + PERSONAL_FILTER_AND_ORDER + """
            </script>
            """)
    IPage<WrongQuestionVO> selectUserPage(Page<WrongQuestionVO> page,
                                          @Param("userId") Long userId,
                                          @Param("filter") PersonalQuestionFilter filter);

    @Select("""
            <script>
            SELECT q.*
            FROM wrong_question w
            JOIN question q ON q.id = w.question_id
            JOIN knowledge_point kp ON kp.id = q.knowledge_point_id
            JOIN chapter c ON c.id = kp.chapter_id
            JOIN subject s ON s.id = c.subject_id
            WHERE w.user_id = #{userId}
              AND q.status = 1
              AND kp.status = 1
              AND c.status = 1
              AND s.status = 1
            """ + PERSONAL_FILTER_AND_ORDER + """
            </script>
            """)
    List<Question> selectPracticeQuestions(@Param("userId") Long userId,
                                           @Param("filter") PersonalQuestionFilter filter);

    default List<Question> selectPracticeQuestions(Long userId) {
        return selectPracticeQuestions(userId, new PersonalQuestionFilter());
    }


    @Select("""
            <script>
            SELECT q.id AS question_id,
                   q.type,
                   q.content,
                   q.difficulty,
                   q.answer AS correct_answer,
                   q.analysis,
                   s.id AS subject_id,
                   s.name AS subject_name,
                   c.id AS chapter_id,
                   c.name AS chapter_name,
                   kp.id AS knowledge_point_id,
                   kp.name AS knowledge_point_name,
                   w.wrong_count,
                   w.last_wrong_at,
                   CASE WHEN q.status = 1 AND kp.status = 1 AND c.status = 1 AND s.status = 1
                        THEN 1 ELSE 0 END AS question_status
            FROM wrong_question w
            JOIN question q ON q.id = w.question_id
            JOIN knowledge_point kp ON kp.id = q.knowledge_point_id
            JOIN chapter c ON c.id = kp.chapter_id
            JOIN subject s ON s.id = c.subject_id
            WHERE w.user_id = #{userId}
            """ + PERSONAL_FILTER_AND_ORDER + """
            </script>
            """)
    List<WrongQuestionVO> selectUserList(@Param("userId") Long userId, @Param("filter") PersonalQuestionFilter filter);
}
