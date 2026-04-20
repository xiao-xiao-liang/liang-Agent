package com.liang.agent.model.dto;

/**
 * 质量评审结果
 * <p>
 * DeepResearch Agent 在 Critique 阶段由 LLM 生成的评审结论。
 * passed=true 表示研究结果充分，可进入总结阶段；
 * passed=false 表示需要继续迭代研究。
 * </p>
 *
 * @param passed   是否通过评审
 * @param feedback 未通过时的反馈意见（指出需补充的研究方向）
 */
public record CritiqueResult(
        boolean passed,
        String feedback
) {
}
