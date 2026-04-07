package com.liang.agent.service.conversation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.agent.common.convention.exception.ClientException;
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
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 会话标题最大长度
     */
    private static final int MAX_TITLE_LENGTH = 100;

    @Override
    public Conversation getOrCreateConversation(String conversationId, String agentType, String firstQuestion) {
        // 先尝试查找已有会话
        Conversation existing = findByConversationId(conversationId);

        if (existing != null) {
            // 已有会话，更新 lastTime
            updateLastTime(conversationId);
            return existing;
        }

        // 首次提问，创建新会话
        Conversation conversation = new Conversation();
        conversation.setConversationId(conversationId);
        conversation.setAgentType(agentType);
        // 标题截取，firstQuestion 为空时使用默认标题
        String title = StringUtils.isBlank(firstQuestion) ? "新会话" : (firstQuestion.length() > MAX_TITLE_LENGTH
                ? firstQuestion.substring(0, MAX_TITLE_LENGTH)
                : firstQuestion);
        conversation.setTitle(title);
        conversation.setLastTime(LocalDateTime.now());

        try {
            save(conversation);
        } catch (DuplicateKeyException e) {
            // 并发创建场景：唯一索引拦截第二次插入，回退查询已有记录
            log.info("会话已被并发创建, conversationId={}", conversationId);
            return findByConversationId(conversationId);
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
        Conversation conversation = findByConversationId(conversationId);
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

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteConversation(String conversationId) {
        // 检查会话是否存在
        Conversation existing = findByConversationId(conversationId);
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
     * 根据 conversationId 查询会话
     */
    private Conversation findByConversationId(String conversationId) {
        return getOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getConversationId, conversationId));
    }

    /**
     * 批量构建 ConversationListVO
     */
    private List<ConversationListVO> buildConversationListVOs(List<Conversation> conversations) {
        if (conversations.isEmpty()) {
            return List.of();
        }

        return conversations.stream()
                .map(c -> new ConversationListVO(
                        c.getConversationId(),
                        c.getAgentType(),
                        c.getTitle(),
                        c.getLastTime(),
                        c.getCreateTime()
                ))
                .toList();
    }
}
