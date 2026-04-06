package com.liang.agent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索结果 DTO
 * 封装 Tavily 搜索引擎返回的单条搜索结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    /** 来源链接 */
    private String url;

    /** 标题 */
    private String title;

    /** 内容摘要 */
    private String content;
}
