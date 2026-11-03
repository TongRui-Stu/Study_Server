package com.tongrui.study.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.function.Function;

@Data
@AllArgsConstructor
public class PageVO<T> {
    private List<T> records;
    private long total;
    private long current;
    private long size;
    private long pages;

    public static <E, T> PageVO<T> from(IPage<E> page, Function<E, T> converter) {
        return new PageVO<>(page.getRecords().stream().map(converter).toList(),
                page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }
}
