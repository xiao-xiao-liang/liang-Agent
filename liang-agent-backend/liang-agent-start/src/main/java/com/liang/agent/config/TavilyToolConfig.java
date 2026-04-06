package com.liang.agent.config;

import com.alibaba.fastjson2.JSON;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 原生 Tavily REST API 工具配置
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(TavilyProperties.class)
public class TavilyToolConfig {

    private final TavilyProperties tavilyProperties;

    /**
     * MCP/Tool 要求的方法入参格式
     */
    @Data
    public static class TavilySearchRequest {
        private String query;
    }

    /**
     * 将 HttpClient 托管给 Spring，随容器生灭，避免警告并提高复用率
     */
    @Bean
    public HttpClient globalHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 将 Tavily 原生 API 包装成 Spring AI 的 ToolCallback
     */
    @Bean
    public List<ToolCallback> tavilyToolCallbacks(HttpClient httpClient) {
        log.info("初始化原生 Tavily REST 工具");

        ToolCallback searchTool = FunctionToolCallback.builder("tavily_search", (TavilySearchRequest req) -> {
                    log.info("执行 Tavily 联网搜索, query={}", req.getQuery());
                    try {
                        String apiKey = tavilyProperties.getApiKey();
                        if (apiKey == null || apiKey.isBlank()) {
                            // 使用统一 JSON 格式返回错误，避免抛出异常致使外层 Agent 被强制中断
                            return JSON.toJSONString(Map.of("error", "Tavily API Key 未配置，请设置环境变量 TAVILY_API_KEY"));
                        }

                        String apiUrl = tavilyProperties.getApiUrl() != null
                                ? tavilyProperties.getApiUrl()
                                : "https://api.tavily.com/search";

                        Map<String, Object> requestBody = Map.of(
                                "api_key", apiKey.strip(),
                                "query", req.getQuery(),
                                "include_answer", false,
                                "include_raw_content", false,
                                "max_results", 5
                        );

                        HttpRequest httpRequest = HttpRequest.newBuilder()
                                .uri(URI.create(apiUrl))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(requestBody)))
                                .timeout(Duration.ofSeconds(30))
                                .build();

                        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                        if (response.statusCode() != 200) {
                            log.error("Tavily 调用失败 ({}): {}", response.statusCode(), response.body());
                            return JSON.toJSONString(Map.of("error", "Tavily 搜索失败, HTTP " + response.statusCode()));
                        }

                        return response.body();
                    } catch (Exception e) {
                        log.error("Tavily 搜索请求异常", e);
                        // 使用 Map 并进行 JSON 序列化，绝对防止了双引号转义解析崩溃的安全隐患
                        return JSON.toJSONString(Map.of("error", "Tavily 搜索异常: " + e.getMessage()));
                    }
                })
                .description("使用 Tavily 进行互联网搜索。在需要获取最新的事实、新闻时调用此工具。必须使用中文搜索。")
                .inputType(TavilySearchRequest.class)
                .build();

        return List.of(searchTool);
    }
}
