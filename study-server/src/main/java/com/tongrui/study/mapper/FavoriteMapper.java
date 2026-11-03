package com.tongrui.study.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tongrui.study.dto.PersonalQuestionFilter;
import com.tongrui.study.entity.Favorite;
import com.tongrui.study.entity.Question;
import com.tongrui.study.vo.FavoriteVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {

    String PERSONAL_FILTER_AND_ORDER = """
            <if test="filter.subjectId != null">AND s.id = #{filter.subjectId}</if>
            <if test="filter.chapterId != null">AND c.id = #{filter.chapterId}</if>
            <if test="filter.knowledgePointId != null">AND kp.id = #{filter.knowledgePointId}</if>
            <if test="filter.type != null and filter.type != ''">AND q.type = #{filter.type}</if>
            <if test="filter.difficulty != null">AND q.difficulty = #{filter.difficulty}</if>
            <if test="filter.keyword != null and filter.keyword != ''">AND q.content LIKE CONCAT('%', #{filter.keyword}, '%')</if>
            <choose>
                <when test="filter.favoriteTimeOrder == 'ASC'">ORDER BY f.created_at ASC, f.id ASC</when>
                <otherwise>ORDER BY f.created_at DESC, f.id DESC</otherwise>
            </choose>
            """;

    @Insert("""
            INSERT INTO favorite (user_id, question_id, created_at)
            VALUES (#{userId}, #{questionId}, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIfAbsent(@Param("userId") Long userId, @Param("questionId") Long questionId);

    @Delete("""
            DELETE FROM favorite
            WHERE user_id = #{userId} AND question_id = #{questionId}
            """)
    int deleteByUserAndQuestion(@Param("userId") Long userId, @Param("questionId") Long questionId);

    @Select("""
            SELECT COUNT(*)
            FROM favorite
            WHERE user_id = #{userId} AND question_id = #{questionId}
            """)
    long countByUserAndQuestion(@Param("userId") Long userId, @Param("questionId") Long questionId);

    @Select("""
            <script>
            SELECT q.id AS question_id,
                   q.type,
                   q.content,
                   q.difficulty,
                   s.id AS subject_id,
                   s.name AS subject_name,
                   c.id AS chapter_id,
                   c.name AS chapter_name,
                   kp.id AS knowledge_point_id,
                   kp.name AS knowledge_point_name,
                   f.created_at,
                   CASE WHEN q.status = 1 AND kp.status = 1 AND c.status = 1 AND s.status = 1
                        THEN 1 ELSE 0 END AS question_status
            FROM favorite f
            JOIN question q ON q.id = f.question_id
            JOIN knowledge_point kp ON kp.id = q.knowledge_point_id
            JOIN chapter c ON c.id = kp.chapter_id
            JOIN subject s ON s.id = c.subject_id
            WHERE f.user_id = #{userId}
            """ + PERSONAL_FILTER_AND_ORDER + """
            </script>
            """)
    IPage<FavoriteVO> selectUserPage(Page<FavoriteVO> page,
                                     @Param("userId") Long userId,
                                     @Param("filter") PersonalQuestionFilter filter);

    @Select("""
            <script>
            SELECT q.*
            FROM favorite f
            JOIN question q ON q.id = f.question_id
            JOIN knowledge_point kp ON kp.id = q.knowledge_point_id
            JOIN chapter c ON c.id = kp.chapter_id
            JOIN subject s ON s.id = c.subject_id
            WHERE f.user_id = #{userId}
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
                   s.id AS subject_id,
                   s.name AS subject_name,
                   c.id AS chapter_id,
                   c.name AS chapter_name,
                   kp.id AS knowledge_point_id,
                   kp.name AS knowledge_point_name,
                   f.created_at,
                   CASE WHEN q.status = 1 AND kp.status = 1 AND c.status = 1 AND s.status = 1
                        THEN 1 ELSE 0 END AS question_status
            FROM favorite f
            JOIN question q ON q.id = f.question_id
            JOIN knowledge_point kp ON kp.id = q.knowledge_point_id
            JOIN chapter c ON c.id = kp.chapter_id
            JOIN subject s ON s.id = c.subject_id
            WHERE f.user_id = #{userId}
            """ + PERSONAL_FILTER_AND_ORDER + """
            </script>
            """)
    List<FavoriteVO> selectUserList(@Param("userId") Long userId, @Param("filter") PersonalQuestionFilter filter);
}
