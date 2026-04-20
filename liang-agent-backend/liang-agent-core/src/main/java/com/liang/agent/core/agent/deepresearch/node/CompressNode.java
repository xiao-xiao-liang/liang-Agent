package com.liang.agent.core.agent.deepresearch.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.liang.agent.core.agent.deepresearch.DeepResearchContext;
import com.liang.agent.core.prompt.DeepResearchPrompts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 上下文压缩节点
 * <p>
 * 当消息上下文超过字符阈值时，调用 LLM 压缩为关键信息摘要。
 * </p>
 */
@Slf4j
@Component
public class CompressNode implements NodeAction {

    private static final int CONTEXT_CHAR_LIMIT = 50000;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) throws Exception {
        DeepResearchContext ctx = DeepResearchContext.fromState(state);
        List<Object> messages = (List<Object>) state.value("messages").orElse(Collections.emptyList());
        int totalChars = messages.stream().mapToInt(m -> m.toString().length()).sum();

        if (totalChars < CONTEXT_CHAR_LIMIT) {
            ctx.emitThinking("\n--- 准备进入下一轮迭代 ---\n");
            return Collections.emptyMap();
        }

        ctx.emitThinking("\n📦 上下文过长（" + totalChars + " 字符），正在压缩...\n");

        String fullContext = messages.stream().map(Object::toString).collect(Collectors.joining("\n\n"));

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(DeepResearchPrompts.getCurrentTime()),
                new SystemMessage("""
                        ##最大压缩限制（必须遵守）
                        -你输出的最终内容【总字符数（包含所有标签、空格、换行）】
                        不得超过：%s
                        - 这是硬性上限，不是建议
                        - 如超过该限制，视为压缩失败

                        """.formatted(CONTEXT_CHAR_LIMIT) + DeepResearchPrompts.COMPRESS),
                new UserMessage(fullContext)
        ));

        String compressed = ctx.getChatModel().call(prompt).getResult().getOutput().getText();
        ctx.emitThinking("✅ 上下文压缩完成\n--- 准备进入下一轮迭代 ---\n");

        return Map.of("messages", List.of("【Compressed Agent State】\n" + compressed));
    }
}
