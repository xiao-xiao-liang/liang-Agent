package com.liang.agent.model.vo;

import java.time.LocalDateTime;

/**
 * 会话列表 VO
 * <p>
 * 用于会话侧边栏展示，包含会话标识、标题、类型和时间。
 * </p>
 *
 * @param conversationId 会话ID
 * @param agentType      智能体类型
 * @param title          会话标题
 * @param lastTime       最近消息时间
 * @param createTime     创建时间
 */
public record ConversationListVO(
        String conversationId,
        String agentType,
        String title,
        LocalDateTime lastTime,
        LocalDateTime createTime
) {
}
