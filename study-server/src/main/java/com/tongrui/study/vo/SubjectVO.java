package com.tongrui.study.vo;

import com.tongrui.study.entity.Subject;
import lombok.Data;

@Data
public class SubjectVO {
    private Long id;
    private String name;
    private String description;
    private Integer sortOrder;
    private Integer status;

    public static SubjectVO from(Subject subject) {
        SubjectVO vo = new SubjectVO();
        vo.setId(subject.getId());
        vo.setName(subject.getName());
        vo.setDescription(subject.getDescription());
        vo.setSortOrder(subject.getSortOrder());
        vo.setStatus(subject.getStatus());
        return vo;
    }
}
