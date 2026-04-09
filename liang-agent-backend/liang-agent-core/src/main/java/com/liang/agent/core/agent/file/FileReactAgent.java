package com.liang.agent.core.agent.file;

import com.liang.agent.common.response.AgentResponse;
import com.liang.agent.core.agent.BaseAgent;
import com.liang.agent.core.prompt.ReactAgentPrompts;
import com.liang.agent.service.conversation.ConversationService;
import com.liang.agent.service.message.ChatMessageService;
import com.liang.agent.service.task.AgentTaskManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件问答 ReAct Agent
 * <p>
 * 基于 ReAct（Reasoning + Acting）模式的文件问答智能体。
 * 通过 {@code FileContentService.loadContent} 工具加载文件内容或进行 RAG 检索，
 * 然后基于文件内容生成回答。
 * </p>
 * <p>
 * 架构与 {@code WebSearchReactAgent} 一致：虚拟线程 + blockLast 同步循环。
 * </p>
 * <p><b>线程安全性：NOT thread-safe。</b>
 * 每次请求由 Controller 创建独立实例。</p>
 */
@Slf4j
public class FileReactAgent extends BaseAgent {

    /**
     * 最大 ReAct 轮次
     */
    private static final int MAX_ROUNDS = 3;

    /**
     * 工具回调列表（FileContentService）
     */
    private final List<ToolCallback> toolCallbacks;

    /**
     * 当前处理的文件ID
     */
    private final String fileId;

    /**
     * 当前轮次
     */
    private final AtomicInteger currentRound = new AtomicInteger(0);

    /**
     * 文本缓冲（正文）
     */
    private final StringBuilder textBuffer = new StringBuilder();

    /**
     * 思考缓冲
     */
    private final StringBuilder thinkingBuffer = new StringBuilder();

    /**
     * 当前轮次的 ToolCall 缓冲（toolCallId → arguments JSON）
     */
    private final Map<String, StringBuilder> toolCallArgsBuffers = new HashMap<>();

    /**
     * 当前轮次的 ToolCall 函数名映射（toolCallId → functionName）
     */
    private final Map<String, String> toolCallNames = new HashMap<>();

    /**
     * 当前输出模式
     */
    private OutputMode outputMode = OutputMode.TEXT;

    private enum OutputMode {
        TEXT, TOOL_CALL
    }

    public FileReactAgent(ChatModel chatModel,
                          ConversationService conversationService,
                          ChatMessageService chatMessageService,
                          AgentTaskManager taskManager,
                          List<ToolCallback> toolCallbacks,
                          String fileId) {
        super("FileReactAgent", chatModel, "file", conversationService, chatMessageService, taskManager);
        this.toolCallbacks = toolCallbacks;
        this.fileId = fileId;
    }

    @Override
    public Flux<String> execute(String conversationId, String query) {
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();

        // 注册任务（并发控制）
        taskManager.register(conversationId, sink);

        // 虚拟线程执行
        Thread.startVirtualThread(() -> {
            this.currentConversationId = conversationId;
            startTimer();
            try {
                // 1. 加载历史对话
                loadChatHistory(conversationId);
                // 2. 确保会话存在 + 保存用户消息
                saveUserMessage(conversationId, query, fileId);
                // 3. 开始 ReAct 循环
                executeReactLoop(sink, conversationId, query);
            } catch (Exception e) {
                log.error("FileReactAgent 执行异常: conversationId={}, fileId={}", conversationId, fileId, e);
                emitError(sink, "Agent 执行异常: " + e.getMessage());
            } finally {
                taskManager.removeTask(conversationId);
            }
        });

        return sink.asFlux();
    }

    /**
     * 执行 ReAct 循环
     */
    private void executeReactLoop(Sinks.Many<String> sink, String conversationId, String query) {
        // 初始化消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(ReactAgentPrompts.getFilePrompt()));

        // 加载历史消息
        List<Message> historyMessages = chatMemory.get(conversationId);
        if (!historyMessages.isEmpty())
            messages.addAll(historyMessages);

        // 注入用户问题和文件ID
        messages.add(new UserMessage("<question>" + query + "</question>"));
        messages.add(new UserMessage("<fileid>" + fileId + "</fileid>"));

        while (currentRound.incrementAndGet() <= MAX_ROUNDS) {
            log.info("[{}] 第 {} 轮开始, conversationId={}, fileId={}",
                    name, currentRound.get(), conversationId, fileId);

            resetRoundState();

            // 构建含工具的 ChatOptions
            ChatOptions chatOptions = ToolCallingChatOptions.builder()
                    .toolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
                    .internalToolExecutionEnabled(false)
                    .build();

            Prompt prompt = new Prompt(messages, chatOptions);
            Flux<ChatResponse> responseFlux = chatModel.stream(prompt);

            // 同步收集流式输出
            AtomicBoolean hasResponse = new AtomicBoolean(false);
            responseFlux.doOnNext(response -> {
                hasResponse.set(true);
                processChunk(sink, response);
            }).blockLast();

            if (!hasResponse.get()) {
                log.warn("[{}] 第 {} 轮无响应", name, currentRound.get());
                break;
            }

            // 判断是否需要执行工具调用
            if (outputMode == OutputMode.TOOL_CALL && !toolCallNames.isEmpty()) {
                String toolResults = executeToolCalls(sink);
                log.info("[{}] 第 {} 轮工具调用完成, 结果长度={}", name, currentRound.get(), toolResults.length());

                messages.add(new AssistantMessage(buildToolCallSummary()));
                messages.add(new UserMessage("工具执行结果:\n" + toolResults + "\n\n请根据以上文件内容信息，回答用户的问题。"));
            } else {
                log.info("[{}] 第 {} 轮为最终回答, conversationId={}", name, currentRound.get(), conversationId);
                break;
            }
        }

        // 最大轮次兜底
        if (textBuffer.isEmpty()) {
            forceFinalAnswer(sink, messages, conversationId);
        }

        // 持久化 assistant 消息
        try {
            saveAssistantMessage(textBuffer.toString(), thinkingBuffer.toString(), null, fileId);
        } catch (Exception e) {
            log.error("持久化 assistant 消息失败, conversationId={}", currentConversationId, e);
        }

        // 保存到 ChatMemory
        chatMemory.add(conversationId, List.of(
                new UserMessage(query),
                new AssistantMessage(textBuffer.toString())
        ));

        // 异步生成推荐问题
        generateRecommendQuestions(sink, query, textBuffer.toString());

        // 完成
        sink.tryEmitComplete();
    }

    /**
     * 处理单个流式 chunk
     */
    private void processChunk(Sinks.Many<String> sink, ChatResponse response) {
        if (response == null || response.getResults().isEmpty())
            return;

        Generation generation = response.getResults().getFirst();
        AssistantMessage assistantMessage = generation.getOutput();

        // 处理文本内容
        String content = assistantMessage.getText();
        if (content != null && !content.isEmpty()) {
            recordFirstResponseTime();
            outputMode = OutputMode.TEXT;
            textBuffer.append(content);
            emitNext(sink, AgentResponse.text(content));
        }

        // 处理思考内容
        Map<String, Object> metadata = assistantMessage.getMetadata();
        Object reasoning = metadata.getOrDefault("reasoning_content", metadata.get("thinking"));
        if (reasoning instanceof String thinkingText && !thinkingText.isEmpty()) {
            thinkingBuffer.append(thinkingText);
            emitNext(sink, AgentResponse.thinking(thinkingText));
        }

        // 处理工具调用
        if (!assistantMessage.getToolCalls().isEmpty()) {
            outputMode = OutputMode.TOOL_CALL;
            for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls())
                handleToolCall(toolCall);
        }
    }

    /**
     * 处理工具调用 chunk（累积参数 JSON）
     */
    private void handleToolCall(AssistantMessage.ToolCall toolCall) {
        String toolCallId = toolCall.id();
        String functionName = toolCall.name();

        if (!functionName.isEmpty()) {
            toolCallNames.put(toolCallId, functionName);
            usedTools.add(functionName);
        }

        String arguments = toolCall.arguments();
        toolCallArgsBuffers.computeIfAbsent(toolCallId, k -> new StringBuilder()).append(arguments);
    }

    /**
     * 执行所有累积的工具调用
     */
    private String executeToolCalls(Sinks.Many<String> sink) {
        StringBuilder results = new StringBuilder();

        for (Map.Entry<String, String> entry : toolCallNames.entrySet()) {
            String toolCallId = entry.getKey();
            String functionName = entry.getValue();
            String arguments = toolCallArgsBuffers.getOrDefault(toolCallId, new StringBuilder()).toString();

            log.info("[{}] 执行工具: {}({})", name, functionName,
                    arguments.length() > 100 ? arguments.substring(0, 100) + "..." : arguments);

            // 推送 thinking 事件：正在加载文件内容
            if (functionName.contains("loadContent")) {
                emitNext(sink, AgentResponse.thinking("📂 正在检索文件内容，请稍等...\n"));
                thinkingBuffer.append("📂 正在检索文件内容，请稍等...\n");
            }

            try {
                String result = null;
                for (ToolCallback callback : toolCallbacks) {
                    if (callback.getToolDefinition().name().equals(functionName)) {
                        result = callback.call(arguments);
                        break;
                    }
                }

                if (result != null)
                    results.append("### ").append(functionName).append(" result:\n").append(result).append("\n\n");
            } catch (Exception e) {
                log.error("[{}] 工具 {} 执行失败: {}", name, functionName, e.getMessage());
                results.append("### ").append(functionName).append(" error: ").append(e.getMessage()).append("\n\n");
            }
        }

        return results.toString();
    }

    /**
     * 最大轮次兜底：强制 LLM 不使用工具直接生成最终回答
     */
    private void forceFinalAnswer(Sinks.Many<String> sink, List<Message> messages, String conversationId) {
        log.info("[{}] 已达最大轮次，强制生成最终回答, conversationId={}", name, conversationId);

        messages.add(new UserMessage("""
                你已达到最大推理轮次限制。
                请基于当前已有的上下文信息，直接给出最终答案。
                禁止再调用任何工具。
                如果信息不完整，请合理总结和说明。
                """));

        Prompt finalPrompt = new Prompt(messages);
        chatModel.stream(finalPrompt)
                .doOnNext(response -> processChunk(sink, response))
                .blockLast();
    }

    /**
     * 重置本轮状态
     */
    private void resetRoundState() {
        toolCallArgsBuffers.clear();
        toolCallNames.clear();
        outputMode = OutputMode.TEXT;
    }

    /**
     * 构建工具调用摘要
     */
    private String buildToolCallSummary() {
        StringBuilder summary = new StringBuilder("我调用了以下工具:\n");
        for (Map.Entry<String, String> entry : toolCallNames.entrySet()) {
            summary.append("- ").append(entry.getValue()).append("\n");
        }
        return summary.toString();
    }
}
