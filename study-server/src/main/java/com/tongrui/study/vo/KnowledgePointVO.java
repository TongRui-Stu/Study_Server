package com.tongrui.study.vo;

import com.tongrui.study.entity.KnowledgePoint;
import lombok.Data;

@Data
public class KnowledgePointVO {
    private Long id;
    private Long chapterId;
    private String name;
    private String description;
    private Integer sortOrder;
    private Integer status;

    public static KnowledgePointVO from(KnowledgePoint knowledgePoint) {
        KnowledgePointVO vo = new KnowledgePointVO();
        vo.setId(knowledgePoint.getId());
        vo.setChapterId(knowledgePoint.getChapterId());
        vo.setName(knowledgePoint.getName());
        vo.setDescription(knowledgePoint.getDescription());
        vo.setSortOrder(knowledgePoint.getSortOrder());
        vo.setStatus(knowledgePoint.getStatus());
        return vo;
    }
}
