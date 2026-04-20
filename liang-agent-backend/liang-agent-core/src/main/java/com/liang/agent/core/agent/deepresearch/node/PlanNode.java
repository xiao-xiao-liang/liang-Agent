package com.liang.agent.core.agent.deepresearch.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.liang.agent.core.agent.deepresearch.DeepResearchContext;
import com.liang.agent.core.prompt.DeepResearchPrompts;
import com.liang.agent.model.dto.PlanTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 执行计划生成节点
 * <p>
 * 根据研究主题和上下文（含 Critique 反馈）生成 List&lt;PlanTask&gt;。
 * 使用 BeanOutputConverter 解析 LLM 输出的 JSON。
 * </p>
 */
@Slf4j
@Component
public class PlanNode implements NodeAction {

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) throws Exception {
        DeepResearchContext ctx = DeepResearchContext.fromState(state);
        int round = (int) state.value("round").orElse(0) + 1;
        ctx.emitThinking("\n🔄 第 " + round + " 轮研究开始\n📋 正在生成执行计划...\n");

        String question = (String) state.value("question").orElse("");
        String topic = (String) state.value("refinedTopic").orElse(question);
        String feedback = (String) state.value("critiqueFeedback").orElse("");

        // 构建上下文
        StringBuilder contextBuilder = new StringBuilder();
        List<Object> messages = (List<Object>) state.value("messages").orElse(Collections.emptyList());
        for (Object msg : messages) {
            contextBuilder.append(msg.toString()).append("\n");
        }

        String toolDesc = renderToolDescriptions(ctx);

        BeanOutputConverter<List<PlanTask>> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(DeepResearchPrompts.getCurrentTime()),
                new SystemMessage(DeepResearchPrompts.PLAN + """
                        ## 当前上下文
                        当前轮次: %s

                        ## 可用工具说明（仅用于规划参考）
                        %s

                        ## 输出格式
                        %s
                        """.formatted(round, toolDesc, converter.getFormat())),
                new UserMessage("""
                        【研究主题】
                        %s

                        【对话历史】
                        %s

                        %s
                        """.formatted(
                        topic,
                        contextBuilder.toString(),
                        feedback.isEmpty() ? "" : """
                                ## 重要约束
                                存在【Critique Feedback】，你必须：
                                1. 仔细分析反馈中指出的不足
                                2. 新的计划必须直接解决这些问题
                                3. 不要重复之前失败的尝试

                                【Critique Feedback】
                                %s
                                """.formatted(feedback)
                ))
        ));

        String json = ctx.getChatClient().prompt().messages(prompt.getInstructions()).call().content();
        List<PlanTask> planTasks;
        try {
            planTasks = converter.convert(json);
        } catch (Exception e) {
            log.warn("Plan JSON 解析失败，尝试手动解析: {}", e.getMessage());
            planTasks = Collections.emptyList();
        }

        ctx.emitThinking("\n✅ 执行计划已生成，共 " + (planTasks != null ? planTasks.size() : 0) + " 个任务\n");
        if (planTasks != null && !planTasks.isEmpty()) {
            StringBuilder planText = new StringBuilder("\n📋 执行计划表：\n");
            for (PlanTask task : planTasks) {
                planText.append(String.format("  🟠 %s \n", task.instruction()));
            }
            ctx.emitThinking(planText.toString());
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("planTasks", planTasks != null ? planTasks : Collections.emptyList());
        updates.put("round", round);
        return updates;
    }

    /**
     * 渲染工具描述（供 LLM 参考可用工具）
     */
    private String renderToolDescriptions(DeepResearchContext ctx) {
        if (ctx.getToolCallbacks() == null || ctx.getToolCallbacks().isEmpty()) {
            return "（当前无可用工具）";
        }
        StringBuilder sb = new StringBuilder();
        for (ToolCallback tool : ctx.getToolCallbacks()) {
            sb.append("- ").append(tool.getToolDefinition().name())
                    .append(": ").append(tool.getToolDefinition().description()).append("\n");
        }
        return sb.toString();
    }
}
