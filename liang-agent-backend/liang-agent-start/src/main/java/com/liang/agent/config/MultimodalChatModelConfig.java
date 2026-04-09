package com.liang.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多模态聊天模型配置
 * <p>
 * 创建用于图片识别的 qwen-vl 系列多模态模型 Bean。
 * </p>
 */
@Slf4j
@Configuration
public class MultimodalChatModelConfig {

    @Value("${multimodal.base-url:https://dashscope.aliyuncs.com/compatible-mode/}")
    private String baseUrl;

    @Value("${multimodal.model:qwen-vl-plus}")
    private String model;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    /**
     * 创建多模态聊天模型 Bean
     *
     * @return OpenAiChatModel 实例（用于图片识别）
     */
    @Bean("multimodalChatModel")
    public OpenAiChatModel multimodalChatModel() {
        try {
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .temperature(0.2d)
                    .model(model)
                    .build();
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(OpenAiApi.builder()
                            .baseUrl(baseUrl)
                            .apiKey(new SimpleApiKey(apiKey))
                            .build())
                    .defaultOptions(options)
                    .build();
            log.info("多模态模型初始化成功: model={}, baseUrl={}", model, baseUrl);
            return chatModel;
        } catch (Exception e) {
            log.warn("多模态模型初始化失败: {}", e.getMessage());
            return null;
        }
    }
}
