package com.tongrui.study.enums;

import com.tongrui.study.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Locale;

public enum PracticeSource {
    KNOWLEDGE_POINT,
    WRONG,
    FAVORITE;

    public static PracticeSource from(String value) {
        if (!StringUtils.hasText(value)) {
            return KNOWLEDGE_POINT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "练习来源不合法");
        }
    }
}
