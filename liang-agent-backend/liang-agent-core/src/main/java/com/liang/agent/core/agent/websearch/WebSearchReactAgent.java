package com.liang.agent.core.agent.websearch;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.liang.agent.common.response.AgentResponse;
import com.liang.agent.core.agent.BaseAgent;
import com.liang.agent.core.prompt.ReactAgentPrompts;
import com.liang.agent.model.dto.SearchResult;
import com.liang.agent.service.message.ChatMessageService;
import com.liang.agent.service.conversation.ConversationService;
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
 * WebSearch ReAct Agent
 * <p>
 * 基于 ReAct（Reasoning + Acting）模式的联网搜索智能体。
 * 通过 REST API 调用 Tavily 搜索引擎，获取实时互联网信息并生成回答。
 * </p>
 * <p>
 * 工作流程：
 * <ol>
 *   <li>加载历史对话 → 构建 ChatMemory</li>
 *   <li>发起流式请求 → 接收 LLM 逐 token 输出</li>
 *   <li>遇到 ToolCall → 执行搜索 → 将结果注入上下文</li>
 *   <li>多轮迭代直到 LLM 给出最终回答或达到最大轮次</li>
 *   <li>持久化回答 → 异步生成推荐问题</li>
 * </ol>
 * <p><b>线程安全性：NOT thread-safe。</b>
 * 每次请求由 Controller 创建独立实例，实例内部所有可变状态（textBuffer、thinkingBuffer、
 * toolCallArgsBuffers 等）被限制在单个虚拟线程中操作，不可跨线程共享实例。</p>
 */
@Slf4j
public class WebSearchReactAgent extends BaseAgent {

    /**
     * 最大 ReAct 轮次
     */
    private static final int MAX_ROUNDS = 5;

    /**
     * MCP 工具回调列表
     */
    private final List<ToolCallback> toolCallbacks;

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
     * 搜索结果收集
     */
    private final List<SearchResult> allSearchResults = new ArrayList<>();

    /**
     * 当前轮次的 ToolCall 缓冲（toolCallId → arguments JSON）
     */
    private final Map<String, StringBuilder> toolCallArgsBuffers = new HashMap<>();

    /**
     * 当前轮次的 ToolCall 函数名映射（toolCallId → functionName）
     */
    private final Map<String, String> toolCallNames = new HashMap<>();

    /**
     * 当前输出模式（TEXT 正文 / TOOL_CALL 工具调用）
     */
    private OutputMode outputMode = OutputMode.TEXT;

    private enum OutputMode {
        TEXT, TOOL_CALL
    }

    public WebSearchReactAgent(ChatModel chatModel,
                               ConversationService conversationService,
                               ChatMessageService chatMessageService,
                               AgentTaskManager taskManager,
                               List<ToolCallback> toolCallbacks) {
        super("WebSearchReactAgent", chatModel, "websearch", conversationService, chatMessageService, taskManager);
        this.toolCallbacks = toolCallbacks;
    }

    @Override
    public Flux<String> execute(String conversationId, String query) {
        // 创建 Sink
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();

        // 注册任务（并发控制，冲突时抛 ServiceException）
        taskManager.register(conversationId, sink);

        // 异步执行（所有对实例状态的写入均在虚拟线程内部，避免跨线程可见性问题）
        Thread.startVirtualThread(() -> {
            this.currentConversationId = conversationId;
            startTimer();
            try {
                // 1. 加载历史对话
                loadChatHistory(conversationId);
                // 2. 确保会话存在 + 保存用户消息
                saveUserMessage(conversationId, query);
                // 3. 开始 ReAct 循环
                executeReactLoop(sink, conversationId, query);
            } catch (Exception e) {
                log.error("WebSearchReactAgent 执行异常: conversationId={}", conversationId, e);
                emitError(sink, "Agent 执行异常: " + e.getMessage());
            } finally {
                taskManager.removeTask(conversationId);
            }
        });

        return sink.asFlux();
    }

    /**
     * 执行 ReAct 循环
     * 每一轮：构建 Prompt → 流式调用 LLM → 处理输出 → 判断是否需要执行工具
     */
    private void executeReactLoop(Sinks.Many<String> sink, String conversationId, String query) {
        // 初始化消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(ReactAgentPrompts.getWebSearchPrompt()));

        // 加载 ChatMemory 中的历史消息（Spring AI 1.1.x get() 只接受 conversationId）
        List<Message> historyMessages = chatMemory.get(conversationId);
        if (!historyMessages.isEmpty()) {
            messages.addAll(historyMessages);
        }
        messages.add(new UserMessage(query));

        while (currentRound.incrementAndGet() <= MAX_ROUNDS) {
            log.info("[{}] 第 {} 轮开始, conversationId={}", name, currentRound.get(), conversationId);

            // 重置本轮状态
            resetRoundState();

            // 构建 ChatOptions 并执行流式调用
            ChatOptions chatOptions = ToolCallingChatOptions.builder()
                    .toolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
                    .internalToolExecutionEnabled(false)
                    .build();

            Prompt prompt = new Prompt(messages, chatOptions);
            Flux<ChatResponse> responseFlux = chatModel.stream(prompt);

            // 实时同步收集本轮流式输出，解决假流式（一口气蹦出来）的问题
            AtomicBoolean hasResponse = new AtomicBoolean(false);
            responseFlux.doOnNext(response -> {
                hasResponse.set(true);
                processChunk(sink, response);
            }).blockLast();

            if (!hasResponse.get()) {
                log.warn("[{}] 第 {} 轮无响应", name, currentRound.get());
                break;
            }

            // 本轮结束，判断是否需要执行工具调用
            if (outputMode == OutputMode.TOOL_CALL && !toolCallNames.isEmpty()) {
                // 执行工具调用（内部会推送 "🔍 正在搜索信息: xxx" 的 thinking 事件）
                String toolResults = executeToolCalls(sink);
                log.info("[{}] 第 {} 轮工具调用完成, 结果长度={}", name, currentRound.get(), toolResults.length());

                // 将助理消息和工具结果加入上下文
                messages.add(new AssistantMessage(buildToolCallSummary()));
                messages.add(new UserMessage("Tool results:\n" + toolResults + "\n\nPlease analyze the above search results and provide a comprehensive answer."));
            } else {
                // 没有工具调用，说明 LLM 给出了最终回答
                log.info("[{}] 第 {} 轮为最终回答, conversationId={}", name, currentRound.get(), conversationId);
                break;
            }
        }

        // 最大轮次兜底：如果循环耗尽且最后一轮是工具调用（textBuffer 为空），强制 LLM 无工具生成最终回答
        if (textBuffer.isEmpty()) {
            forceFinalAnswer(sink, messages, conversationId);
        }

        // 发送参考链接（只发送 url + title，不含 content 全文）
        if (!allSearchResults.isEmpty()) {
            String referenceForClient = buildLightweightReference();
            emitNext(sink, AgentResponse.reference(referenceForClient, allSearchResults.size()));
        }

        // 持久化 assistant 消息（reference 只存 url+title，避免超出数据库列长度限制）
        String referenceJson = allSearchResults.isEmpty() ? null : buildLightweightReference();
        try {
            saveAssistantMessage(textBuffer.toString(), thinkingBuffer.toString(), referenceJson);
        } catch (Exception e) {
            // 持久化失败不影响用户看到回答，仅记录日志
            log.error("持久化 assistant 消息失败, conversationId={}, error={}", currentConversationId, e.getMessage());
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
        if (response == null || response.getResults().isEmpty()) {
            return;
        }

        Generation generation = response.getResults().getFirst();
        AssistantMessage assistantMessage = generation.getOutput();

        // 处理文本内容
        String content = assistantMessage.getText();
        if (content != null && !content.isEmpty()) {
            recordFirstResponseTime();
            outputMode = OutputMode.TEXT;
            handleTextContent(sink, content);
        }

        Map<String, Object> metadata = assistantMessage.getMetadata();
        Object reasoning = metadata.getOrDefault("reasoning_content", metadata.get("thinking"));
        if (reasoning instanceof String thinkingText) {
            appendThinking(sink, thinkingText);
        }

        // 处理工具调用
        if (!assistantMessage.getToolCalls().isEmpty()) {
            outputMode = OutputMode.TOOL_CALL;
            for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                handleToolCall(toolCall);
            }
        }
    }

    /**
     * 处理文本内容
     * <p>
     * 检查 metadata 中是否有思考内容（部分模型会通过 metadata 返回 reasoning/thinking），
     * 正文内容追加到 textBuffer 并流式输出。
     * </p>
     */
    private void handleTextContent(Sinks.Many<String> sink, String content) {
        textBuffer.append(content);
        emitNext(sink, AgentResponse.text(content));
    }

    /**
     * 追加思考内容到缓冲，并发送到流
     */
    private void appendThinking(Sinks.Many<String> sink, String thinking) {
        if (thinking != null && !thinking.isEmpty()) {
            thinkingBuffer.append(thinking);
            emitNext(sink, AgentResponse.thinking(thinking));
        }
    }

    /**
     * 处理工具调用 chunk（累积参数 JSON）
     */
    private void handleToolCall(AssistantMessage.ToolCall toolCall) {
        String toolCallId = toolCall.id();
        String functionName = toolCall.name();

        // 记录函数名
        if (!functionName.isEmpty()) {
            toolCallNames.put(toolCallId, functionName);
            usedTools.add(functionName);
        }

        // 累积参数
        String arguments = toolCall.arguments();
        toolCallArgsBuffers.computeIfAbsent(toolCallId, k -> new StringBuilder()).append(arguments);
    }

    /**
     * 执行所有累积的工具调用
     * <p>
     * 对搜索类工具，会先提取 query 参数并推送 thinking 事件（"🔍 正在搜索信息: xxx"），
     * 保持与 dodo-agent 一致的用户反馈体验。
     * </p>
     *
     * @param sink SSE 事件推送器
     * @return 工具执行结果文本
     */
    private String executeToolCalls(Sinks.Many<String> sink) {
        StringBuilder results = new StringBuilder();

        for (Map.Entry<String, String> entry : toolCallNames.entrySet()) {
            String toolCallId = entry.getKey();
            String functionName = entry.getValue();
            String arguments = toolCallArgsBuffers.getOrDefault(toolCallId, new StringBuilder()).toString();

            log.info("[{}] 执行工具: {}({})", name, functionName, arguments.length() > 100 ? arguments.substring(0, 100) + "..." : arguments);

            // 搜索类工具：提取 query 参数，推送 thinking 事件
            if (functionName.contains("search")) {
                emitSearchThinking(sink, arguments);
            }

            try {
                // 查找对应的 ToolCallback 并执行
                String result = null;
                for (ToolCallback callback : toolCallbacks) {
                    if (callback.getToolDefinition().name().equals(functionName)) {
                        result = callback.call(arguments);
                        break;
                    }
                }

                if (result != null) {
                    results.append("### ").append(functionName).append(" result:\n").append(result).append("\n\n");
                    // 解析搜索结果
                    parseSearchResults(result);
                }
            } catch (Exception e) {
                log.error("[{}] 工具 {} 执行失败: {}", name, functionName, e.getMessage());
                results.append("### ").append(functionName).append(" error: ").append(e.getMessage()).append("\n\n");
            }
        }

        return results.toString();
    }

    /**
     * 推送搜索 thinking 事件
     * <p>
     * 从工具参数中提取搜索关键词，推送 "🔍 正在搜索信息: xxx" 的 thinking 消息，
     * 让前端在搜索执行期间有实时反馈。
     * </p>
     */
    private void emitSearchThinking(Sinks.Many<String> sink, String arguments) {
        try {
            JSONObject args = JSON.parseObject(arguments);
            String query = args != null ? args.getString("query") : null;
            String thinkingText = (query != null && !query.isBlank())
                    ? "🔍 正在搜索信息: " + query + "\n"
                    : "🔍 正在搜索相关信息\n";
            appendThinking(sink, thinkingText);
        } catch (Exception e) {
            appendThinking(sink, "🔍 正在搜索相关信息\n");
        }
    }

    /**
     * 最大轮次兜底：强制 LLM 不使用工具直接生成最终回答
     * <p>
     * 当 ReAct 循环因达到 MAX_ROUNDS 退出且最后一轮仍是工具调用（textBuffer 为空）时，
     * 追加一条限制 Prompt，要求 LLM 基于已有上下文直接总结回答。
     * </p>
     */
    private void forceFinalAnswer(Sinks.Many<String> sink, List<Message> messages, String conversationId) {
        log.info("[{}] 已达最大轮次，强制生成最终回答, conversationId={}", name, conversationId);

        messages.add(new UserMessage("""
                你已达到最大推理轮次限制。
                请基于当前已有的上下文信息，直接给出最终答案。
                禁止再调用任何工具。
                如果信息不完整，请合理总结和说明。
                """));

        // 不带工具选项的 Prompt，防止 LLM 再次发起工具调用
        Prompt finalPrompt = new Prompt(messages);
        chatModel.stream(finalPrompt)
                .doOnNext(response -> processChunk(sink, response))
                .blockLast();
    }

    /**
     * 解析原生 Tavily 搜索结果
     */
    private void parseSearchResults(String toolResult) {
        try {
            JSONObject responseJson = JSON.parseObject(toolResult);
            if (responseJson == null) return;

            JSONArray resultsArray = responseJson.getJSONArray("results");
            if (resultsArray != null) {
                for (int i = 0; i < resultsArray.size(); i++) {
                    JSONObject item = resultsArray.getJSONObject(i);
                    allSearchResults.add(new SearchResult(
                            item.getString("url"),
                            item.getString("title"),
                            item.getString("content")
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("原生搜索结果解析失败 (可能存在异常响应): {}", e.getMessage());
        }
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
     * 构建工具调用摘要（作为助理消息加入上下文）
     */
    private String buildToolCallSummary() {
        StringBuilder summary = new StringBuilder("I called the following tools:\n");
        for (Map.Entry<String, String> entry : toolCallNames.entrySet()) {
            summary.append("- ").append(entry.getValue()).append("\n");
        }
        return summary.toString();
    }

    /**
     * 构建精简的引用 JSON（仅包含 url + title）
     * <p>
     * 排除 content 全文以避免：
     * <ul>
     *   <li>数据库 reference 列溢出（MysqlDataTruncation）</li>
     *   <li>SSE 一次性推送大量文本导致前端卡顿</li>
     * </ul>
     * </p>
     */
    private String buildLightweightReference() {
        List<Map<String, String>> lightList = allSearchResults.stream()
                .map(sr -> Map.of("url", sr.url(), "title", sr.title()))
                .toList();
        return JSON.toJSONString(lightList);
    }
}
