package com.liang.agent.service.conversation;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liang.agent.model.entity.Conversation;
import com.liang.agent.model.vo.ConversationListVO;
import com.liang.agent.service.message.ChatMessageService;

import java.util.List;

/**
 * 会话服务接口
 * <p>
 * 仅负责会话级元信息的管理，消息操作由 {@link ChatMessageService} 负责。
 * </p>
 */
public interface ConversationService extends IService<Conversation> {

    /**
     * 获取或创建会话
     * <p>
     * 首次提问时自动创建会话记录（设置 title = 首条问题），
     * 后续提问直接返回已有会话并更新 lastTime。
     * </p>
     *
     * @param conversationId 会话ID
     * @param agentType      智能体类型
     * @param firstQuestion  首条用户问题（作为会话标题）
     * @return 会话实体
     */
    Conversation getOrCreateConversation(String conversationId, String agentType, String firstQuestion);

    /**
     * 查询会话列表（按最近消息时间倒序）
     *
     * @return 会话列表 VO
     */
    List<ConversationListVO> listConversations();

    /**
     * 删除会话（逻辑删除，级联删除所有消息）
     *
     * @param conversationId 会话ID
     */
    void deleteConversation(String conversationId);

    /**
     * 更新会话的最近消息时间
     *
     * @param conversationId 会话ID
     */
    void updateLastTime(String conversationId);
}
