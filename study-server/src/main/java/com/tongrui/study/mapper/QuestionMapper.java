package com.tongrui.study.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tongrui.study.entity.Question;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    @Select("""
            SELECT
                (SELECT COUNT(*) FROM answer_record WHERE question_id = #{questionId}) +
                (SELECT COUNT(*) FROM wrong_question WHERE question_id = #{questionId}) +
                (SELECT COUNT(*) FROM favorite WHERE question_id = #{questionId})
            """)
    long countLearningRecords(@Param("questionId") Long questionId);
}
