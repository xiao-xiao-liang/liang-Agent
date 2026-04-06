package com.liang.agent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话实体
 * <p>
 * 只存储会话级元信息，不包含具体消息内容。
 * 与 {@link ChatMessage} 形成一对多关系：conversation(1) → message(N)
 * </p>
 */
@Data
@TableName("conversation")
public class Conversation {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 会话唯一标识（UUID，由前端生成）
     */
    private String conversationId;

    /**
     * 智能体类型（websearch/file/deepresearch/pptx）
     */
    private String agentType;

    /**
     * 会话标题（取自首条用户问题）
     */
    private String title;

    /**
     * 最近消息时间（冗余字段，用于列表排序）
     */
    private LocalDateTime lastTime;

    /**
     * 创建时间（自动填充）
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间（自动填充）
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除 0:正常 1:删除
     */
    @TableLogic
    private Integer deleted;
}
