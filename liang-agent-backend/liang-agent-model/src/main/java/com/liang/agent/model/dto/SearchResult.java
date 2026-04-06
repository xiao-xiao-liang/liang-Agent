package com.liang.agent.model.dto;

/**
 * 搜索结果 DTO
 * <p>
 * 封装 Tavily 搜索引擎返回的单条搜索结果，创建后不可变。
 * </p>
 *
 * @param url     来源链接
 * @param title   标题
 * @param content 内容摘要
 */
public record SearchResult(
        String url,
        String title,
        String content
) {
}
