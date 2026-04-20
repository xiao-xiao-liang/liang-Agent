package com.liang.agent.core.agent.deepresearch.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.liang.agent.core.agent.deepresearch.DeepResearchContext;
import com.liang.agent.core.prompt.DeepResearchPrompts;
import com.liang.agent.model.dto.CritiqueResult;
import com.liang.agent.model.dto.PlanTask;
import com.liang.agent.model.dto.TaskResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 质量评审节点
 * <p>
 * 判断当前研究结果是否充分，输出 CritiqueResult。
 * 驱动条件路由：通过 → summarize，未通过 → compress → plan 循环。
 * </p>
 */
@Slf4j
@Component
public class CritiqueNode implements NodeAction {

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) throws Exception {
        DeepResearchContext ctx = DeepResearchContext.fromState(state);
        ctx.emitThinking("\n🔍 正在评估当前研究结果...\n");

        BeanOutputConverter<CritiqueResult> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
        });

        String question = (String) state.value("question").orElse("");
        String topic = (String) state.value("refinedTopic").orElse("");
        List<PlanTask> planTasks = (List<PlanTask>) state.value("planTasks").orElse(Collections.emptyList());
        Map<String, TaskResult> taskResults = (Map<String, TaskResult>) state.value("taskResults").orElse(Collections.emptyMap());

        StringBuilder userMessage = new StringBuilder();
        userMessage.append("【用户原始问题】\n").append(question);
        userMessage.append("\n\n【研究主题】\n").append(topic.isEmpty() ? "未生成研究主题" : topic);

        userMessage.append("\n\n【当前轮次的执行计划】\n");
        for (PlanTask task : planTasks) {
            userMessage.append(String.format("- %s\n", task.instruction()));
        }

        userMessage.append("\n\n【当前轮次的工具结果】\n");
        for (Map.Entry<String, TaskResult> entry : taskResults.entrySet()) {
            TaskResult result = entry.getValue();
            if (result.success() && result.output() != null) {
                userMessage.append(String.format("任务 %s: %s\n\n", entry.getKey(), result.output()));
            } else if (!result.success() && result.error() != null) {
                userMessage.append(String.format("任务 %s: 执行失败 - %s\n\n", entry.getKey(), result.error()));
            }
        }

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(DeepResearchPrompts.getCurrentTime()),
                new SystemMessage(DeepResearchPrompts.CRITIQUE + "\n" + converter.getFormat()),
                new UserMessage(userMessage.toString())
        ));

        String raw = ctx.getChatClient().prompt(prompt).call().content();

        CritiqueResult critiqueResult;
        try {
            critiqueResult = converter.convert(raw);
        } catch (Exception e) {
            log.warn("Critique JSON 解析失败，默认通过: {}", e.getMessage());
            critiqueResult = new CritiqueResult(true, "解析失败，默认通过");
        }

        if (critiqueResult.passed()) {
            ctx.emitThinking("\n✅ 研究结果评估通过，准备生成最终报告\n");
        } else {
            ctx.emitThinking("\n⚠️ 研究结果评估未通过，原因分析：" + critiqueResult.feedback() + "\n");
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("critiquePassed", critiqueResult.passed());
        updates.put("critiqueFeedback", critiqueResult.feedback() != null ? critiqueResult.feedback() : "");

        if (!critiqueResult.passed()) {
            updates.put("messages", List.of("【Critique Feedback】\n" + critiqueResult.feedback()));
        }

        return updates;
    }
}
