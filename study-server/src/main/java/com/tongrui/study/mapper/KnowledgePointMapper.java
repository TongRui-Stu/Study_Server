package com.tongrui.study.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tongrui.study.entity.KnowledgePoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KnowledgePointMapper extends BaseMapper<KnowledgePoint> {

    @Select("SELECT COUNT(*) FROM question WHERE knowledge_point_id = #{knowledgePointId}")
    long countQuestions(Long knowledgePointId);
}
