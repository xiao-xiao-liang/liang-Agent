package com.liang.agent.service.message;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liang.agent.model.entity.ChatMessage;
import com.liang.agent.model.vo.MessageVO;

import java.util.List;

/**
 * 会话消息服务接口
 */
public interface ChatMessageService extends IService<ChatMessage> {

    /**
     * 保存用户消息
     *
     * @param conversationId 会话ID
     * @param content        用户问题
     * @return 保存后的消息实体
     */
    ChatMessage saveUserMessage(String conversationId, String content);

    /**
     * 保存 AI 助手消息
     *
     * @param chatMessage 助手消息（需提前设置 conversationId、content、thinking、tools 等字段）
     * @return 保存后的消息实体（含自动生成的 id）
     */
    ChatMessage saveAssistantMessage(ChatMessage chatMessage);

    /**
     * 更新消息的推荐问题
     *
     * @param messageId    消息ID
     * @param recommendJson 推荐问题 JSON
     */
    void updateRecommend(Long messageId, String recommendJson);

    /**
     * 查询指定会话的最近消息（用于 ChatMemory 加载）
     *
     * @param conversationId 会话ID
     * @param maxRecords     最大记录数
     * @return 消息列表，按创建时间倒序
     */
    List<ChatMessage> findRecentMessages(String conversationId, int maxRecords);

    /**
     * 查询会话的所有消息（用于会话详情展示）
     *
     * @param conversationId 会话ID
     * @return 消息 VO 列表，按创建时间正序
     */
    List<MessageVO> getMessagesByConversationId(String conversationId);

    /**
     * 删除指定会话的所有消息（逻辑删除）
     *
     * @param conversationId 会话ID
     */
    void deleteByConversationId(String conversationId);
}
