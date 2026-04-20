package com.liang.agent.core.agent.deepresearch.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.liang.agent.common.response.AgentResponse;
import com.liang.agent.core.agent.deepresearch.DeepResearchContext;
import com.liang.agent.core.prompt.DeepResearchPrompts;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 最终报告生成节点
 * <p>
 * LLM 流式调用，token 级推送到 sink（type=text），生成深度研究报告。
 * </p>
 */
@Component
public class SummarizeNode implements NodeAction {

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) throws Exception {
        DeepResearchContext ctx = DeepResearchContext.fromState(state);
        ctx.emitThinking("\n✅ 研究阶段完成，准备生成最终报告\n📝 正在生成最终研究报告...\n\n");

        String question = (String) state.value("question").orElse("");
        String topic = (String) state.value("refinedTopic").orElse("");

        List<Object> messages = (List<Object>) state.value("messages").orElse(Collections.emptyList());
        StringBuilder toolResults = new StringBuilder();
        for (Object msg : messages) {
            String text = msg.toString();
            if (text.contains("【Completed Task Result】")) {
                toolResults.append(text).append("\n\n");
            }
        }

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(DeepResearchPrompts.getCurrentTime()),
                new SystemMessage(DeepResearchPrompts.SUMMARIZE),
                new UserMessage("""
                        【用户原始问题】
                        %s

                        【研究主题】
                        %s

                        【工具检索结果】
                        %s
                        """.formatted(
                        question,
                        topic.isEmpty() ? "未生成研究主题" : topic,
                        toolResults.isEmpty() ? "（未检索到相关结果）" : toolResults.toString()
                ))
        ));

        StringBuilder textBuffer = ctx.getTextBuffer();
        Flux<ChatResponse> responseFlux = ctx.getChatModel().stream(prompt);
        responseFlux.doOnNext(chunk -> {
            if (chunk == null || chunk.getResults().isEmpty()) {
                return;
            }
            Generation gen = chunk.getResults().getFirst();
            String text = gen.getOutput().getText();
            if (text != null) {
                textBuffer.append(text);
                ctx.emitNext(AgentResponse.text(text));
            }
        }).blockLast();

        return Map.of("textBuffer", textBuffer.toString());
    }
}
