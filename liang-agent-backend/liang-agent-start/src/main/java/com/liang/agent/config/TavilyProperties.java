package com.liang.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tavily MCP 配置属性
 * <p>
 * 通过 @ConfigurationProperties 集中管理，替代散落在 Controller 中的 @Value。
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "tavily")
public class TavilyProperties {

    /**
     * 原生 API 地址
     */
    private String apiUrl;

    /**
     * API 密钥
     */
    private String apiKey;
}
