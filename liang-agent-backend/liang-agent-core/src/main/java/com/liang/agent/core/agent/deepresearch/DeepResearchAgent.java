package com.liang.agent.core.agent.deepresearch;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.fastjson2.JSON;
import com.liang.agent.common.response.AgentResponse;
import com.liang.agent.core.agent.BaseAgent;
import com.liang.agent.model.dto.SearchResult;
import com.liang.agent.service.message.ChatMessageService;
import com.liang.agent.service.conversation.ConversationService;
import com.liang.agent.service.task.AgentTaskManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepResearch Agent（深度研究智能体）
 * <p>
 * 基于 Spring AI Alibaba Graph 的 StateGraph 进行流程编排，
 * 实现 Plan-Execute-Critique 循环的深度研究模式。
 * </p>
 * <p>
 * 职责：
 * <ol>
 *   <li>创建 {@link DeepResearchContext}（请求级共享资源），放入 OverAllState</li>
 *   <li>编译并执行 {@link StateGraph}（由 {@link DeepResearchConfiguration} 定义为 @Bean）</li>
 *   <li>图执行完成后的后处理（持久化、推荐问题、SSE 完成）</li>
 * </ol>
 * </p>
 * <p><b>线程安全性：NOT thread-safe。</b>每次请求由 Controller 创建独立实例。</p>
 */
@Slf4j
public class DeepResearchAgent extends BaseAgent {

    /**
     * 搜索工具回调列表
     */
    private final List<ToolCallback> toolCallbacks;

    /**
     * StateGraph 定义（@Bean 单例，由 Configuration 注入）
     */
    private final StateGraph stateGraph;

    /**
     * 最大 Plan-Execute-Critique 循环轮次
     */
    private static final int MAX_ROUNDS = 3;

    public DeepResearchAgent(ChatModel chatModel,
                             ConversationService conversationService,
                             ChatMessageService chatMessageService,
                             AgentTaskManager taskManager,
                             List<ToolCallback> toolCallbacks,
                             StateGraph stateGraph) {
        super("DeepResearchAgent", chatModel, "deepresearch", conversationService, chatMessageService, taskManager);
        this.toolCallbacks = toolCallbacks;
        this.stateGraph = stateGraph;
    }

    @Override
    public Flux<String> execute(String conversationId, String query) {
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        taskManager.register(conversationId, sink);

        Thread.startVirtualThread(() -> {
            this.currentConversationId = conversationId;
            startTimer();

            // 创建请求级共享上下文
            DeepResearchContext ctx = DeepResearchContext.builder()
                    .sink(sink)
                    .chatClient(ChatClient.builder(chatModel).build())
                    .chatModel(chatModel)
                    .toolCallbacks(toolCallbacks)
                    .build();

            try {
                // 1. 加载历史对话
                loadChatHistory(conversationId);
                // 2. 确保会话存在 + 保存用户消息
                saveUserMessage(conversationId, query, null);
                // 3. 编译 StateGraph（@Bean 定义的图结构，编译后可执行）
                CompiledGraph compiledGraph = stateGraph.compile();
                // 4. 以初始状态执行图（ctx 通过 OverAllState 传递给所有 Node）
                Map<String, Object> inputs = new HashMap<>();
                inputs.put(DeepResearchContext.STATE_KEY, ctx);
                inputs.put("question", query);
                inputs.put("conversationId", conversationId);
                inputs.put("round", 0);
                inputs.put("maxRounds", MAX_ROUNDS);

                compiledGraph.invoke(inputs);

                // 5. 后处理
                handlePostProcessing(sink, ctx, query);

            } catch (Exception e) {
                log.error("DeepResearchAgent 执行异常: conversationId={}", conversationId, e);
                emitError(sink, "深度研究执行异常: " + e.getMessage());
            } finally {
                taskManager.removeTask(conversationId);
            }
        });

        return sink.asFlux();
    }

    /**
     * 图执行完成后的后处理
     */
    private void handlePostProcessing(Sinks.Many<String> sink, DeepResearchContext ctx, String query) {
        String text = ctx.getTextBuffer().toString();
        String thinking = ctx.getThinkingBuffer().toString();
        List<SearchResult> searchResults = ctx.getAllSearchResults();

        // 记录首次响应时间
        recordFirstResponseTime();

        // 发送参考链接
        if (!searchResults.isEmpty()) {
            List<Map<String, String>> lightList = searchResults.stream()
                    .map(sr -> Map.of("url", sr.url(), "title", sr.title()))
                    .toList();
            emitNext(sink, AgentResponse.reference(JSON.toJSONString(lightList), searchResults.size()));
        }

        // 持久化 assistant 消息
        String referenceJson = searchResults.isEmpty() ? null
                : JSON.toJSONString(searchResults.stream()
                .map(sr -> Map.of("url", sr.url(), "title", sr.title()))
                .toList());
        try {
            saveAssistantMessage(text, thinking, referenceJson, null);
        } catch (Exception e) {
            log.error("持久化 assistant 消息失败: {}", e.getMessage());
        }

        // 保存到 ChatMemory
        if (!text.isEmpty()) {
            chatMemory.add(currentConversationId, List.of(
                    new UserMessage(query),
                    new AssistantMessage(text)
            ));
        }

        // 推荐问题
        if (!text.isEmpty()) {
            generateRecommendQuestions(sink, query, text);
        }

        // 完成 SSE 流
        sink.tryEmitComplete();
    }
}
