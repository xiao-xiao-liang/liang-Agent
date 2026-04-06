package com.liang.agent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Agent 对话请求 DTO
 *
 * @param query          用户问题
 * @param conversationId 会话ID（首次对话可为空，后端自动生成）
 */
public record AgentChatRequest(
        @NotBlank(message = "查询内容不能为空")
        @Size(max = 5000, message = "问题长度不能超过 5000 字符")
        String query,

        String conversationId
) {
}
