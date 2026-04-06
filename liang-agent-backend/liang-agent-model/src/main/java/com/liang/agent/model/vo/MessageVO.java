package com.liang.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话消息展示 VO
 * <p>
 * 用于前端展示单条对话消息。
 * 根据 role 字段判断消息方向：user（用户） / assistant（AI）。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO {

    /** 消息ID */
    private Long id;

    /** 消息角色：user / assistant */
    private String role;

    /** 消息内容 */
    private String content;

    /** 思考过程（仅 assistant） */
    private String thinking;

    /** 使用的工具（仅 assistant） */
    private String tools;

    /** 参考链接 JSON（仅 assistant） */
    private String reference;

    /** 推荐问题 JSON（仅 assistant） */
    private String recommend;

    /** 关联文件ID */
    private String fileId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
