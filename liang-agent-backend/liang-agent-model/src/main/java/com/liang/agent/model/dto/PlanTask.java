package com.liang.agent.model.dto;

/**
 * 执行计划条目
 * <p>
 * DeepResearch Agent 在 Plan 阶段由 LLM 生成的任务条目。
 * order 相同的任务可并行执行，不同 order 的任务串行执行。
 * 当 id 为 null 时表示无需继续工具检索，可进入总结阶段。
 * </p>
 *
 * @param id          任务唯一标识（如 "task-1"）
 * @param instruction 任务指令（描述需调用哪个工具、查什么信息）
 * @param order       执行顺序（相同 order 并行，不同 order 串行）
 */
public record PlanTask(
        String id,
        String instruction,
        int order
) {
}
