package com.liang.agent.core.agent.deepresearch.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.liang.agent.core.agent.deepresearch.DeepResearchContext;
import com.liang.agent.core.prompt.DeepResearchPrompts;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 研究主题生成节点
 * <p>
 * 将用户问题拆解为 3-5 个可执行的研究维度。
 * </p>
 */
@Component
public class TopicNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        DeepResearchContext ctx = DeepResearchContext.fromState(state);
        ctx.emitThinking("\n📝 正在生成研究主题...\n");

        String question = (String) state.value("question").orElse("");

        List<Message> messages = List.of(
                new SystemMessage(DeepResearchPrompts.getCurrentTime()),
                new SystemMessage(DeepResearchPrompts.RESEARCH_TOPIC_GENERATION),
                new UserMessage("<original_question>" + question + "</original_question>")
        );

        String topic = ctx.getChatClient().prompt().messages(messages).call().content();
        ctx.emitThinking(topic + "\n✅ 研究主题已生成\n\n");

        return Map.of("refinedTopic", topic);
    }
}
