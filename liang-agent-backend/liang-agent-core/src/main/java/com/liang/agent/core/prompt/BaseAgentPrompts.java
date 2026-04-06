package com.liang.agent.core.prompt;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 通用 Agent 提示词常量
 * <p>
 * 包含所有 Agent 共享的规则约束，与 dodo-agent 保持一致。
 * </p>
 */
public final class BaseAgentPrompts {

    private BaseAgentPrompts() {
    }

    /**
     * 通用系统时间提示
     * <p>
     * 每次调用动态生成，注入当前服务器时间，解决 LLM 训练数据截止导致的日期认知错误。
     * </p>
     */
    public static String getSystemTimePrompt() {
        return """
                ## 当前系统时间
                %s
                """.formatted(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    }

    /**
     * 工具调用规范
     */
    public static final String TOOL_CALLING_RULES = """
            ## 工具调用规范
            1. 如需调用工具：必须使用 ToolCall 结构，且只能通过工具调用字段输出
            2. 工具调用时：禁止在 content 中出现任何工具调用文本
            3. 工具调用消息必须一次性、原子性输出，不得混杂任何解释
            4. 参数必须简洁有效的 JSON
            5. 当需要多路独立信息时，务必**并行 (PARALLEL)**调用相关工具
            6. 请进行深入思考以确定所需的**最少工具调用集合**
            7. 只有当某个工具的输出结果是下一个工具必备的输入参数时，才采用串行调用

            ## 工具执行结果
            系统会自动将工具执行结果注入上下文，你只需读取并决定下一步动作。
            """;

    /**
     * 最终回答规范
     */
    public static final String FINAL_ANSWER_RULES = """
            ## 最终回答规范
            1. 当上下文已有全部信息时，不要再调用工具
            2. 输出最终自然语言答案，禁止包含工具调用格式
            3. 禁止重复调用同一个工具，除非失败
            4. 回答要综合全面但用词必须精准简炼
            5. 使用 Markdown 格式确保易读性
            6. 通过多级标题和列表逻辑化地组织信息
            7. 必须直接面向用户回答核心诉求，而不只是冰冷地复述工具的结果
            """;

    /**
     * 输出格式规范
     */
    public static final String OUTPUT_SPECIFICATIONS = """
            ## 输出格式规范
            1. 尽可能的使用 emoji 表情，让回答更友好
            2. 使用结构化方式呈现信息（列表、表格、分类等）
            3. 对关键内容进行强调加粗说明
            4. 保持回答的清晰度和易读性
            5. 尽可能全面详细的回答用户问题
            6. 原文中存在引用来源时请予以标注
            """;

    /**
     * 强制要求
     */
    public static final String MANDATORY_REQUIREMENTS = """
            ## 强制要求
            1. 工具调用必须只通过 ToolCall 字段输出
            2. 本轮无工具调用时，必须输出最终答案
            3. 禁止输出干扰解析的结构
            4. 已有全部信息时，不要再调用工具
            """;

    /**
     * 推荐问题提示词
     */
    public static final String RECOMMEND_PROMPT = """
            根据 用户的问题 和 你的回答，生成3个相关的推荐问题。推荐问题应该是用户可能感兴趣的后续问题。
            
            要求：
            1. 问题要与原始问题和回答内容相关
            2. 问题要有实际价值，能引导深入探讨
            3. 问题要简洁明了，一般不超过20个字
            4. 问题应该有一定的多样性
            5. 问题不要重复，也不要与当前会话中的问题完全相同
            
            请直接返回3个问题，每个问题用换行分隔，不要添加序号或其他格式。
            
            用户的问题: {question}
            
            你的回答: {answer}
            """;
}
