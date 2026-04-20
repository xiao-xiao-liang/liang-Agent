package com.liang.agent.core.agent.deepresearch;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.liang.agent.common.response.AgentResponse;
import com.liang.agent.model.dto.SearchResult;
import lombok.Builder;
import lombok.Getter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DeepResearch 请求级共享上下文
 * <p>
 * 封装单次深度研究请求中所有节点共享的资源。
 * 通过 OverAllState 的 {@value #STATE_KEY} 键在节点间传递。
 * 每个 Node 通过 {@link #fromState(OverAllState)} 获取实例。
 * </p>
 */
@Getter
@Builder
public class DeepResearchContext {

    /**
     * 在 OverAllState 中存储 DeepResearchContext 的键名
     */
    public static final String STATE_KEY = "_ctx";

    /**
     * SSE 流式推送器
     */
    private final Sinks.Many<String> sink;

    /**
     * LLM 聊天客户端
     */
    private final ChatClient chatClient;

    /**
     * LLM 聊天模型（用于直接调用 stream）
     */
    private final ChatModel chatModel;

    /**
     * 搜索工具回调列表
     */
    private final List<ToolCallback> toolCallbacks;

    /**
     * 最终报告文本缓冲
     */
    @Builder.Default
    private final StringBuilder textBuffer = new StringBuilder();

    /**
     * 思考过程缓冲
     */
    @Builder.Default
    private final StringBuilder thinkingBuffer = new StringBuilder();

    /**
     * 搜索结果收集（线程安全）
     */
    @Builder.Default
    private final List<SearchResult> allSearchResults = Collections.synchronizedList(new ArrayList<>());

    /**
     * 已使用的工具名集合
     */
    @Builder.Default
    private final Set<String> usedTools = new HashSet<>();

    // ==================== 辅助方法 ====================

    /**
     * 从 OverAllState 中提取 DeepResearchContext
     * <p>
     * 所有 Node 通过此方法获取请求级上下文，
     * 保证 Node 是无状态的 @Component 单例。
     * </p>
     *
     * @param state 当前 OverAllState
     * @return DeepResearchContext 实例
     * @throws IllegalStateException 如果 OverAllState 中未找到上下文
     */
    public static DeepResearchContext fromState(OverAllState state) {
        return (DeepResearchContext) state.value(STATE_KEY)
                .orElseThrow(() -> new IllegalStateException("OverAllState 中未找到 DeepResearchContext"));
    }

    /**
     * 推送 thinking 事件并追加到缓冲区
     */
    public void emitThinking(String content) {
        thinkingBuffer.append(content);
        emitNext(AgentResponse.thinking(content));
    }

    /**
     * 向 Sink 推送消息
     */
    public void emitNext(String data) {
        Sinks.EmitResult result = sink.tryEmitNext(data);
        if (result.isFailure()) {
            // 推送失败仅记录，不中断流程
        }
    }
}
