package com.liang.agent.core.prompt;

/**
 * 通用 Agent 提示词常量
 * 所有 Agent 共享的规则约束
 */
public final class BaseAgentPrompts {

    private BaseAgentPrompts() {
    }

    /**
     * 工具调用规范
     */
    public static final String TOOL_CALLING_RULES = """
            ## 工具调用规范
            1. 当需要多路独立信息时，务必**并行 (PARALLEL)**调用相关工具
            2. 请进行深入思考以确定所需的**最少工具调用集合**
            3. 一次性同时发起所有的独立工具调用，而非排队串行等待
            4. 只有当某个工具的输出结果是下一个工具必备的输入参数时，才采用串行调用
            """;

    /**
     * 最终回答规范
     */
    public static final String FINAL_ANSWER_RULES = """
            ## 最终回答规范
            在生成最终回复时：
            1. 回答要综合全面但用词必须精准简炼
            2. 使用 Markdown 格式确保易读性
            3. 结合你在工具调用中收集到的所有相关细节
            4. 通过多级标题和列表逻辑化地组织信息
            5. 必须直接面向用户回答核心诉求，而不只是冰冷地复述工具的结果
            """;

    /**
     * 输出格式规范
     */
    public static final String OUTPUT_SPECIFICATIONS = """
            ## 输出格式规范
            - 使用清晰、专业的中文语言表达
            - 在适合的场景使用 Markdown 格式化回复
            - 原文中存在引用来源时请予以标注
            - 善用列表结构罗列要点
            - 使用表格对结构化或对比数据进行展示
            """;

    /**
     * 推荐问题提示词
     */
    public static final String RECOMMEND_PROMPT = """
            根据 用户的问题 和 你的回答，生成3个相关的推荐问题。推荐问题应该是用户可能感兴趣的后续问题。
            
            要求：
            1. 问题要与原始问题和回答内容相关
            2. 问题要有实际价值，能引导深入探讨
            3. 问题要简洁明了
            4. 问题应该有一定的多样性
            
            请直接返回3个问题，每个问题用换行分隔，不要添加序号或其他格式。
            
            用户的问题: {question}
            
            你的回答: {answer}
            """;
}
