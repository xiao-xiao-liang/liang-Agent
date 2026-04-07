package com.liang.agent.service.conversation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.agent.common.convention.exception.ClientException;
import com.liang.agent.model.entity.ChatMessage;
import com.liang.agent.model.entity.Conversation;
import com.liang.agent.model.vo.ConversationListVO;
import com.liang.agent.model.vo.MessageVO;
import com.liang.agent.model.vo.PageResult;
import com.liang.agent.model.vo.ConversationDetailVO;
import com.liang.agent.service.mapper.ConversationMapper;
import com.liang.agent.service.message.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        try {
            save(conversation);
        } catch (DuplicateKeyException e) {
            // 并发创建场景：唯一索引拦截第二次插入，回退查询已有记录
            log.info("会话已被并发创建, conversationId={}", conversationId);
            return getOne(new LambdaQueryWrapper<Conversation>()
                    .eq(Conversation::getConversationId, conversationId));
        }

        log.info("新建会话: conversationId={}, agentType={}", conversationId, agentType);
        return conversation;
    }

    @Override
    public List<ConversationListVO> listConversations() {
        List<Conversation> conversations = list(new LambdaQueryWrapper<Conversation>()
                .orderByDesc(Conversation::getLastTime));

        return buildConversationListVOs(conversations);
    }

    @Override
    public PageResult<ConversationListVO> listConversations(int pageNum, int pageSize) {
        Page<Conversation> page = new Page<>(pageNum, pageSize);
        page(page, new LambdaQueryWrapper<Conversation>()
                .orderByDesc(Conversation::getLastTime));

        List<ConversationListVO> voList = buildConversationListVOs(page.getRecords());

        return new PageResult<>(voList, page.getTotal(), pageNum, pageSize);
    }

    @Override
    public ConversationDetailVO getConversationDetail(String conversationId) {
        // 查询会话元信息
        Conversation conversation = getOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getConversationId, conversationId));
        if (conversation == null) {
            throw new ClientException("会话不存在: " + conversationId);
        }

        // 查询所有消息
        List<MessageVO> messages = chatMessageService.getMessagesByConversationId(conversationId);

        return new ConversationDetailVO(
                conversation.getConversationId(),
                conversation.getAgentType(),
                conversation.getTitle(),
                messages
        );
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

    /**
     * 批量构建 ConversationListVO（含消息数量统计）
     * <p>
     * 通过一次批量查询获取每个会话的消息数，避免 N+1 查询。
     * </p>
     */
    private List<ConversationListVO> buildConversationListVOs(List<Conversation> conversations) {
        if (conversations.isEmpty()) {
            return List.of();
        }

        // 批量查询每个会话的消息数量（一次 SQL 完成，避免 N+1）
        List<String> conversationIds = conversations.stream()
                .map(Conversation::getConversationId)
                .toList();
        Map<String, Long> countMap = chatMessageService.list(
                        new LambdaQueryWrapper<ChatMessage>()
                                .in(ChatMessage::getConversationId, conversationIds)
                                .select(ChatMessage::getConversationId)
                ).stream()
                .collect(Collectors.groupingBy(ChatMessage::getConversationId, Collectors.counting()));

        return conversations.stream()
                .map(c -> new ConversationListVO(
                        c.getConversationId(),
                        c.getAgentType(),
                        c.getTitle(),
                        countMap.getOrDefault(c.getConversationId(), 0L).intValue(),
                        c.getLastTime(),
                        c.getCreateTime()
                ))
                .toList();
    }
}
