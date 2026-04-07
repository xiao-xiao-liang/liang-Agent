package com.liang.agent.model.vo;

import java.util.List;

/**
 * 会话详情 VO
 * <p>
 * 包含会话元信息 + 完整消息列表。
 * </p>
 *
 * @param conversationId 会话ID
 * @param agentType      智能体类型
 * @param title          会话标题
 * @param messages       消息列表
 */
public record ConversationDetailVO(
        String conversationId,
        String agentType,
        String title,
        List<MessageVO> messages
) {
}
