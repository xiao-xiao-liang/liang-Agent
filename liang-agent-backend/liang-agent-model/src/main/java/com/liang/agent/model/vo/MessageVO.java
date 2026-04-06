package com.liang.agent.model.vo;

import java.time.LocalDateTime;

/**
 * 会话消息展示 VO
 * <p>
 * 用于前端展示单条对话消息。
 * 根据 role 字段判断消息方向：user（用户） / assistant（AI）。
 * </p>
 *
 * @param id         消息ID
 * @param role       消息角色：user / assistant
 * @param content    消息内容
 * @param thinking   思考过程（仅 assistant）
 * @param tools      使用的工具（仅 assistant）
 * @param reference  参考链接 JSON（仅 assistant）
 * @param recommend  推荐问题 JSON（仅 assistant）
 * @param fileId     关联文件ID
 * @param createTime 创建时间
 */
public record MessageVO(
        Long id,
        String role,
        String content,
        String thinking,
        String tools,
        String reference,
        String recommend,
        String fileId,
        LocalDateTime createTime
) {
}
