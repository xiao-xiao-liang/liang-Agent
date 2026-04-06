package com.liang.agent.service.conversation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.agent.common.convention.exception.ClientException;
import com.liang.agent.model.entity.Conversation;
import com.liang.agent.model.vo.ConversationListVO;
import com.liang.agent.service.mapper.ConversationMapper;
import com.liang.agent.service.message.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation> implements ConversationService {

    private final ChatMessageService chatMessageService;

    @Override
    public Conversation getOrCreateConversation(String conversationId, String agentType, String firstQuestion) {
        // 先尝试查找已有会话
        Conversation existing = getOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getConversationId, conversationId));

        if (existing != null) {
            // 已有会话，更新 lastTime
            updateLastTime(conversationId);
            return existing;
        }

        // 首次提问，创建新会话
        Conversation conversation = new Conversation();
        conversation.setConversationId(conversationId);
        conversation.setAgentType(agentType);
        // 标题截取前 100 字符
        conversation.setTitle(firstQuestion.length() > 100 ? firstQuestion.substring(0, 100) : firstQuestion);
        conversation.setLastTime(LocalDateTime.now());
        save(conversation);
        log.info("新建会话: conversationId={}, agentType={}", conversationId, agentType);
        return conversation;
    }

    @Override
    public List<ConversationListVO> listConversations() {
        List<Conversation> conversations = list(new LambdaQueryWrapper<Conversation>()
                .orderByDesc(Conversation::getLastTime));

        return conversations.stream()
                .map(c -> ConversationListVO.builder()
                        .conversationId(c.getConversationId())
                        .agentType(c.getAgentType())
                        .title(c.getTitle())
                        .lastTime(c.getLastTime())
                        .createTime(c.getCreateTime())
                        .build())
                .toList();
    }

    @Override
    public void deleteConversation(String conversationId) {
        // 检查会话是否存在
        Conversation existing = getOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getConversationId, conversationId));
        if (existing == null) {
            throw new ClientException("会话不存在: " + conversationId);
        }
        // 级联逻辑删除消息
        chatMessageService.deleteByConversationId(conversationId);
        // 逻辑删除会话
        removeById(existing.getId());
        log.info("会话已删除: conversationId={}", conversationId);
    }

    @Override
    public void updateLastTime(String conversationId) {
        update(new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getConversationId, conversationId)
                .set(Conversation::getLastTime, LocalDateTime.now()));
    }
}
