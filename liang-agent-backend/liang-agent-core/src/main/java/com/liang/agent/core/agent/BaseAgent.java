package com.liang.agent.core.agent;

import com.alibaba.fastjson2.JSON;
import com.liang.agent.common.response.AgentResponse;
import com.liang.agent.core.prompt.BaseAgentPrompts;
import com.liang.agent.model.entity.ChatMessage;
import com.liang.agent.model.enums.MessageRole;
import com.liang.agent.service.message.ChatMessageService;
import com.liang.agent.service.conversation.ConversationService;
import com.liang.agent.service.task.AgentTaskManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Agent 抽象基类
 * <p>
 * 定义所有 Agent 的公共能力：
 * <ul>
 *   <li>聊天记忆管理（从 MySQL 加载历史 → 内存记忆）</li>
 *   <li>Sink 流式输出</li>
 *   <li>会话 + 消息持久化</li>
 *   <li>推荐问题生成</li>
 *   <li>计时器（首次响应时间、总响应时间）</li>
 * </ul>
 * Agent 实例是非 Spring Bean，每次请求由 Controller 创建，通过构造器传入依赖。
 * </p>
 */
@Slf4j
public abstract class BaseAgent {

    /**
     * Agent 名称
     */
    protected final String name;

    /**
     * 聊天模型
     */
    protected final ChatModel chatModel;

    /**
     * 智能体类型标识
     */
    protected final String agentType;

    /**
     * 会话服务
     */
    protected final ConversationService conversationService;

    /**
     * 消息服务
     */
    protected final ChatMessageService chatMessageService;

    /**
     * 任务管理器
     */
    protected final AgentTaskManager taskManager;

    /**
     * 聊天记忆
     */
    protected final ChatMemory chatMemory;

    /**
     * ChatMemory 消息窗口大小
     */
    private static final int CHAT_MEMORY_WINDOW_SIZE = 20;

    /**
     * 从数据库加载的历史消息条数
     */
    private static final int HISTORY_LOAD_SIZE = 10;

    /**
     * 当前会话ID
     */
    protected String currentConversationId;

    /**
     * 当前 assistant 消息ID（数据库主键，用于后续更新推荐问题）
     */
    protected Long currentMessageId;

    /**
     * 本次对话使用的工具集合
     */
    protected final Set<String> usedTools = new HashSet<>();

    /**
     * 计时器 - 开始时间
     */
    protected long startTime;

    /**
     * 计时器 - 首次响应时间
     */
    protected Long firstResponseTime;

    protected BaseAgent(String name, ChatModel chatModel, String agentType,
                        ConversationService conversationService,
                        ChatMessageService chatMessageService,
                        AgentTaskManager taskManager) {
        this.name = name;
        this.chatModel = chatModel;
        this.agentType = agentType;
        this.conversationService = conversationService;
        this.chatMessageService = chatMessageService;
        this.taskManager = taskManager;
        this.chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(CHAT_MEMORY_WINDOW_SIZE)
                .build();
    }

    /**
     * 执行 Agent（由子类实现核心逻辑）
     *
     * @param conversationId 会话ID
     * @param query          用户问题
     * @return SSE 流
     */
    public abstract Flux<String> execute(String conversationId, String query);

    /**
     * 从 MySQL 加载历史消息到 ChatMemory
     * <p>
     * 使用 role 字段直接映射到 Spring AI 的 UserMessage / AssistantMessage。
     * </p>
     */
    protected void loadChatHistory(String conversationId) {
        List<ChatMessage> history = chatMessageService.findRecentMessages(conversationId, HISTORY_LOAD_SIZE);
        if (history.isEmpty()) {
            return;
        }
        // findRecentMessages 返回倒序，需反转为正序加载
        List<ChatMessage> ordered = new ArrayList<>(history);
        Collections.reverse(ordered);

        List<Message> messages = new ArrayList<>();
        for (ChatMessage msg : ordered) {
            if (MessageRole.USER == msg.getRole()) {
                messages.add(new UserMessage(msg.getContent()));
            } else if (MessageRole.ASSISTANT == msg.getRole()) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }
        if (!messages.isEmpty()) {
            chatMemory.add(conversationId, messages);
            log.debug("已加载 {} 条历史消息到 ChatMemory, conversationId={}", messages.size(), conversationId);
        }
    }

    /**
     * 确保会话存在 + 保存用户消息
     */
    protected void saveUserMessage(String conversationId, String query, String fileId) {
        // 确保会话存在（首次自动创建，后续更新 lastTime）
        conversationService.getOrCreateConversation(conversationId, agentType, query);
        // 保存用户消息
        ChatMessage chatMessage = ChatMessage.builder()
                .conversationId(conversationId)
                .role(MessageRole.USER)
                .content(query)
                .fileId(fileId)
                .build();
        chatMessageService.saveUserMessage(chatMessage);
    }

    /**
     * 保存 AI 助手消息（回答完成后调用）
     *
     * @param answer    回答内容
     * @param thinking  思考过程
     * @param reference 参考链接 JSON
     * @param fileId    文件ID（可选）
     */
    protected void saveAssistantMessage(String answer, String thinking, String reference, String fileId) {
        String finalThinking = (thinking != null && !thinking.isBlank()) ? thinking : null;
        String finalTools = usedTools.isEmpty() ? null : String.join(",", usedTools);

        ChatMessage chatMessage = ChatMessage.builder()
                .conversationId(currentConversationId)
                .role(MessageRole.ASSISTANT)
                .content(answer)
                .thinking(finalThinking)
                .tools(finalTools)
                .reference(reference)
                .fileId(fileId)
                .firstResponseTime(firstResponseTime)
                .totalResponseTime(System.currentTimeMillis() - startTime)
                .build();
        ChatMessage saved = chatMessageService.saveAssistantMessage(chatMessage);
        this.currentMessageId = saved.getId();

        // 更新会话的最近消息时间
        conversationService.updateLastTime(currentConversationId);
    }

    /**
     * 向 Sink 推送消息并处理异常
     */
    protected void emitNext(Sinks.Many<String> sink, String data) {
        Sinks.EmitResult result = sink.tryEmitNext(data);
        if (result.isFailure()) {
            log.warn("Sink 推送失败: result={}, conversationId={}", result, currentConversationId);
        }
    }

    /**
     * 向 Sink 推送错误消息并完成
     */
    protected void emitError(Sinks.Many<String> sink, String errorMessage) {
        emitNext(sink, AgentResponse.error(errorMessage));
        sink.tryEmitComplete();
    }

    protected void startTimer() {
        this.startTime = System.currentTimeMillis();
        this.firstResponseTime = null;
    }

    protected void recordFirstResponseTime() {
        if (this.firstResponseTime == null) {
            this.firstResponseTime = System.currentTimeMillis() - this.startTime;
        }
    }

    /**
     * 异步生成推荐问题
     */
    protected void generateRecommendQuestions(Sinks.Many<String> sink, String question, String answer) {
        try {
            String prompt = BaseAgentPrompts.RECOMMEND_PROMPT
                    .replace("{question}", question)
                    .replace("{answer}", answer.length() > 500 ? answer.substring(0, 500) : answer);

            List<Message> messages = List.of(new UserMessage(prompt));
            String response = chatModel.call(new Prompt(messages)).getResult().getOutput().getText();

            if (response != null && !response.isBlank()) {
                String[] questions = response.split("\n");
                List<String> recommendList = new ArrayList<>();
                for (String q : questions) {
                    String trimmed = q.trim();
                    if (!trimmed.isEmpty()) {
                        recommendList.add(trimmed);
                    }
                }
                if (!recommendList.isEmpty()) {
                    emitNext(sink, AgentResponse.recommend(recommendList));
                    // 持久化推荐问题到 assistant 消息
                    chatMessageService.updateRecommend(currentMessageId, JSON.toJSONString(recommendList));
                }
            }
        } catch (Exception e) {
            log.warn("推荐问题生成失败, conversationId={}, error={}", currentConversationId, e.getMessage());
        }
    }
}
