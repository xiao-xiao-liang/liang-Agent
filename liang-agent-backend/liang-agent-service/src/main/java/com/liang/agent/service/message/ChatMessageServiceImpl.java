package com.liang.agent.service.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.agent.model.entity.ChatMessage;
import com.liang.agent.model.enums.MessageRole;
import com.liang.agent.model.vo.MessageVO;
import com.liang.agent.service.mapper.ChatMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话消息服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatMessageService {

    @Override
    public ChatMessage saveUserMessage(String conversationId, String content) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setConversationId(conversationId);
        chatMessage.setRole(MessageRole.USER);
        chatMessage.setContent(content);
        save(chatMessage);
        return chatMessage;
    }

    @Override
    public ChatMessage saveAssistantMessage(ChatMessage chatMessage) {
        chatMessage.setRole(MessageRole.ASSISTANT);
        save(chatMessage);
        return chatMessage;
    }

    @Override
    public void updateRecommend(Long messageId, String recommendJson) {
        update(new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getId, messageId)
                .set(ChatMessage::getRecommend, recommendJson));
    }

    @Override
    public List<ChatMessage> findRecentMessages(String conversationId, int maxRecords) {
        Page<ChatMessage> page = new Page<>(1, maxRecords);
        page.setSearchCount(false);
        return page(page, new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByDesc(ChatMessage::getCreateTime)
        ).getRecords();
    }

    @Override
    public List<MessageVO> getMessagesByConversationId(String conversationId) {
        List<ChatMessage> chatMessages = list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getCreateTime));

        return chatMessages.stream()
                .map(m -> new MessageVO(
                        m.getId(),
                        m.getRole().getValue(),
                        m.getContent(),
                        m.getThinking(),
                        m.getTools(),
                        m.getReference(),
                        m.getRecommend(),
                        m.getFileId(),
                        m.getCreateTime()
                ))
                .toList();
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        remove(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId));
    }
}
