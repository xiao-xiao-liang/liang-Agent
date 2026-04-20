package com.liang.agent.core.agent.deepresearch.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.liang.agent.common.response.AgentResponse;
import com.liang.agent.core.agent.deepresearch.DeepResearchContext;
import com.liang.agent.core.prompt.DeepResearchPrompts;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 需求澄清节点
 * <p>
 * 判断用户问题是否信息充足，可以开始研究。
 * 输出 clarifyPassed=true/false，驱动条件路由。
 * </p>
 */
@Component
public class ClarifyNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        DeepResearchContext ctx = DeepResearchContext.fromState(state);
        ctx.emitThinking("\n🔍 正在分析您的需求...\n");

        String question = (String) state.value("question").orElse("");

        List<Message> messages = List.of(
                new SystemMessage(DeepResearchPrompts.getCurrentTime()),
                new SystemMessage(DeepResearchPrompts.REQUIREMENT_CLARIFICATION),
                new UserMessage(question)
        );

        String response = ctx.getChatClient().prompt().messages(messages).call().content();
        ctx.emitThinking(response + "\n");

        boolean needsMoreInfo = response != null && response.contains("【需要补充信息】");
        if (needsMoreInfo) {
            String pauseMessage = response.replace("【需要补充信息】", "").trim();
            ctx.emitNext(AgentResponse.text("⏸ " + pauseMessage));
            ctx.emitThinking("\n⏸ 暂停深入研究，等待补充信息\n");
        } else {
            ctx.emitThinking("\n✅ 信息充足，准备生成研究主题\n");
        }

        return Map.of("clarifyPassed", !needsMoreInfo);
    }
}
