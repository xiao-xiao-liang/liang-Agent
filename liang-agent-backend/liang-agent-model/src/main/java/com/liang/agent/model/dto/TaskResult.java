package com.liang.agent.model.dto;

/**
 * 任务执行结果
 * <p>
 * DeepResearch Agent 在 Execute 阶段中每个 PlanTask 的执行结果。
 * </p>
 *
 * @param taskId  任务唯一标识
 * @param success 是否执行成功
 * @param output  成功时的输出内容
 * @param error   失败时的错误信息
 */
public record TaskResult(
        String taskId,
        boolean success,
        String output,
        String error
) {
}
