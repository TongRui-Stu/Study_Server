package com.tongrui.study.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tongrui.study.entity.Subject;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SubjectMapper extends BaseMapper<Subject> {
}