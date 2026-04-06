package com.liang.agent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话消息实体
 * <p>
 * 使用 role 字段区分消息角色：
 * <ul>
 *   <li>user — 用户提问，content 为问题内容</li>
 *   <li>assistant — AI 回复，content 为回答内容，附带思考/工具/引用等元数据</li>
 * </ul>
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_message")
public class ChatMessage {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 所属会话ID
     */
    private String conversationId;

    /**
     * 消息角色：user / assistant
     */
    private String role;

    /**
     * 消息内容（user=问题, assistant=回答）
     */
    private String content;

    /**
     * 思考过程（仅 assistant）
     */
    private String thinking;

    /**
     * 使用的工具名称，逗号分隔（仅 assistant）
     */
    private String tools;

    /**
     * 参考链接 JSON（仅 assistant）
     */
    @TableField("`reference`")
    private String reference;

    /**
     * 推荐问题 JSON（仅 assistant）
     */
    private String recommend;

    /**
     * 关联文件ID
     */
    private String fileId;

    /**
     * 首次响应时间，毫秒（仅 assistant）
     */
    private Long firstResponseTime;

    /**
     * 整体回复时间，毫秒（仅 assistant）
     */
    private Long totalResponseTime;

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
