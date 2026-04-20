package com.liang.agent.core.agent.deepresearch;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.liang.agent.core.agent.deepresearch.node.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * DeepResearch StateGraph 配置类
 * <p>
 * 将 StateGraph 定义为 Spring Bean，在应用启动时构建图结构。
 * 图结构（节点 + 边）是静态的，每次请求通过不同的初始 OverAllState 执行。
 * 节点通过 OverAllState 中的 {@link DeepResearchContext#STATE_KEY} 键获取请求级资源。
 * </p>
 *
 * <pre>
 * 流程图：
 * START → clarify → [条件] → topic → plan → execute → critique → [条件] → summarize → END
 *                      ↓                                              ↓
 *                     END(暂停)                                   compress → plan(循环)
 * </pre>
 */
@Slf4j
@Configuration
public class DeepResearchConfiguration {

    /**
     * 最大 Plan-Execute-Critique 循环轮次
     */
    private static final int DEFAULT_MAX_ROUNDS = 3;

    /**
     * 构建 DeepResearch StateGraph Bean
     * <p>
     * 所有 Node 通过 Spring 容器注入（@Component 单例），
     * 图结构在应用启动时一次性定义，运行时复用。
     * </p>
     */
    @Bean
    public StateGraph deepResearchGraph(ClarifyNode clarifyNode,
                                        TopicNode topicNode,
                                        PlanNode planNode,
                                        ExecuteNode executeNode,
                                        CritiqueNode critiqueNode,
                                        CompressNode compressNode,
                                        SummarizeNode summarizeNode) throws Exception {

        // ===== 状态 Key 策略工厂 =====
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            // 请求级上下文
            strategies.put(DeepResearchContext.STATE_KEY, KeyStrategy.REPLACE);
            // 输入
            strategies.put("question", KeyStrategy.REPLACE);
            strategies.put("conversationId", KeyStrategy.REPLACE);
            // 流程控制
            strategies.put("round", KeyStrategy.REPLACE);
            strategies.put("maxRounds", KeyStrategy.REPLACE);
            strategies.put("clarifyPassed", KeyStrategy.REPLACE);
            strategies.put("critiquePassed", KeyStrategy.REPLACE);
            // 研究数据
            strategies.put("refinedTopic", KeyStrategy.REPLACE);
            strategies.put("planTasks", KeyStrategy.REPLACE);
            strategies.put("taskResults", KeyStrategy.REPLACE);
            strategies.put("critiqueFeedback", KeyStrategy.REPLACE);
            // 上下文（累积）
            strategies.put("messages", KeyStrategy.APPEND);
            // 输出缓冲
            strategies.put("textBuffer", KeyStrategy.REPLACE);
            return strategies;
        };

        // ===== 创建 StateGraph =====
        StateGraph graph = new StateGraph("deep-research", keyStrategyFactory);

        // ===== 7 个 NodeAction 节点（Spring Bean 单例，async 包装） =====
        graph.addNode("clarify", node_async(clarifyNode));
        graph.addNode("topic", node_async(topicNode));
        graph.addNode("plan", node_async(planNode));
        graph.addNode("execute", node_async(executeNode));
        graph.addNode("critique", node_async(critiqueNode));
        graph.addNode("compress", node_async(compressNode));
        graph.addNode("summarize", node_async(summarizeNode));

        // ===== 边（编排） =====
        graph.addEdge(StateGraph.START, "clarify");

        // 需求澄清 → 条件路由
        graph.addConditionalEdges("clarify",
                edge_async(state -> Boolean.TRUE.equals(state.value("clarifyPassed").orElse(false)) ? "topic" : "__end__"),
                Map.of("topic", "topic", "__end__", StateGraph.END));

        // 固定边
        graph.addEdge("topic", "plan");
        graph.addEdge("plan", "execute");
        graph.addEdge("execute", "critique");

        // 质量评审 → 条件路由（核心循环）
        graph.addConditionalEdges("critique",
                edge_async(state -> {
                    boolean passed = Boolean.TRUE.equals(state.value("critiquePassed").orElse(true));
                    int round = (int) state.value("round").orElse(0);
                    int max = (int) state.value("maxRounds").orElse(DEFAULT_MAX_ROUNDS);
                    return (passed || round >= max) ? "summarize" : "compress";
                }),
                Map.of("summarize", "summarize", "compress", "compress"));

        // 压缩 → 回到计划（循环回边）
        graph.addEdge("compress", "plan");
        graph.addEdge("summarize", StateGraph.END);

        log.info("DeepResearch StateGraph 定义完成，节点数=7");
        return graph;
    }
}
