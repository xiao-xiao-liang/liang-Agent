package com.liang.agent.model.vo;

import java.util.List;

/**
 * 通用分页结果
 *
 * @param records  当前页数据
 * @param total    总记录数
 * @param pageNum  当前页码
 * @param pageSize 每页大小
 * @param <T>      记录类型
 */
public record PageResult<T>(
        List<T> records,
        long total,
        int pageNum,
        int pageSize
) {
    /**
     * 计算总页数
     */
    public long totalPages() {
        return (total + pageSize - 1) / pageSize;
    }
}
