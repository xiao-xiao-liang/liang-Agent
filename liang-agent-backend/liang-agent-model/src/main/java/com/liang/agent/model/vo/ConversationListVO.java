package com.liang.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话列表 VO
 * <p>
 * 用于会话侧边栏展示，包含会话标识、标题、类型和时间。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationListVO {

    /** 会话ID */
    private String conversationId;

    /** 智能体类型 */
    private String agentType;

    /** 会话标题 */
    private String title;

    /** 最近消息时间 */
    private LocalDateTime lastTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
