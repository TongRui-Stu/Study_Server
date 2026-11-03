package com.tongrui.study.vo;

import com.tongrui.study.entity.Chapter;
import lombok.Data;

@Data
public class ChapterVO {
    private Long id;
    private Long subjectId;
    private String name;
    private String description;
    private Integer sortOrder;
    private Integer status;

    public static ChapterVO from(Chapter chapter) {
        ChapterVO vo = new ChapterVO();
        vo.setId(chapter.getId());
        vo.setSubjectId(chapter.getSubjectId());
        vo.setName(chapter.getName());
        vo.setDescription(chapter.getDescription());
        vo.setSortOrder(chapter.getSortOrder());
        vo.setStatus(chapter.getStatus());
        return vo;
    }
}
