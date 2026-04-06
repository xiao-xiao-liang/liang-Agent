package com.liang.agent.service.task;

import com.liang.agent.common.convention.exception.ServiceException;
import com.liang.agent.common.response.AgentResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 任务管理器
 * <p>
 * 管理每个会话的流式任务生命周期，支持：
 * <ul>
 *   <li>注册：将会话绑定到一个 Sink + Disposable</li>
 *   <li>中断：用户主动停止时取消订阅并关闭 Sink</li>
 *   <li>移除：流完成后清理资源</li>
 *   <li>过期清理：定时清理运行超时的僵尸任务</li>
 * </ul>
 * 同一个 conversationId 同一时刻只允许一个任务执行。
 * </p>
 */
@Slf4j
@Service
public class AgentTaskManager {

    /** 任务最大存活时间（毫秒），超过此时间自动清理 */
    private static final long TASK_TIMEOUT_MS = 5 * 60 * 1000L;

    private final ConcurrentHashMap<String, TaskInfo> runningTasks = new ConcurrentHashMap<>();

    /**
     * 注册任务
     *
     * @param conversationId 会话ID
     * @param sink           SSE 输出通道
     * @throws ServiceException 若已有任务在执行
     */
    public void register(String conversationId, Sinks.Many<String> sink) {
        TaskInfo taskInfo = new TaskInfo();
        taskInfo.setSink(sink);
        taskInfo.setStartTime(System.currentTimeMillis());

        TaskInfo existing = runningTasks.putIfAbsent(conversationId, taskInfo);
        if (existing != null) {
            throw new ServiceException("该会话已有任务在执行中，请稍后再试");
        }
        log.info("任务已注册: conversationId={}", conversationId);
    }

    /**
     * 停止任务（用户中断）
     * <p>
     * 先发送 "⏹ 用户已停止生成" 停止消息给前端，再关闭 Sink 并取消订阅。
     * </p>
     */
    public void stopTask(String conversationId) {
        Optional.ofNullable(runningTasks.remove(conversationId)).ifPresent(taskInfo -> {
            log.info("正在停止任务: conversationId={}", conversationId);
            // 取消响应式订阅
            Optional.ofNullable(taskInfo.getDisposable()).ifPresent(d -> {
                if (!d.isDisposed()) {
                    d.dispose();
                }
            });
            // 发送停止消息给前端，再关闭 Sink
            Sinks.Many<String> sink = taskInfo.getSink();
            if (sink != null) {
                try {
                    sink.tryEmitNext(AgentResponse.text("⏹ 用户已停止生成\n"));
                    sink.tryEmitComplete();
                } catch (Exception e) {
                    log.warn("发送停止消息失败: conversationId={}", conversationId, e);
                }
            }
        });
    }

    /**
     * 移除任务（流正常结束后调用）
     */
    public void removeTask(String conversationId) {
        runningTasks.remove(conversationId);
        log.debug("任务已移除: conversationId={}", conversationId);
    }

    /**
     * 检查会话是否有任务在执行
     */
    public boolean isRunning(String conversationId) {
        return runningTasks.containsKey(conversationId);
    }

    /**
     * 定时清理超时的僵尸任务
     * <p>
     * 防止因异常/OOM 导致 finally 块未执行时，任务永久占据 runningTasks。
     * 每 60 秒扫描一次，清理运行超过 5 分钟的任务。
     * </p>
     */
    @Scheduled(fixedDelay = 60_000)
    public void cleanStaleTasks() {
        if (runningTasks.isEmpty()) return;
        
        long now = System.currentTimeMillis();
        runningTasks.entrySet().removeIf(entry -> {
            long elapsed = now - entry.getValue().getStartTime();
            boolean stale = elapsed > TASK_TIMEOUT_MS;
            if (stale) {
                log.warn("清理过期任务: conversationId={}, 已运行{}ms", entry.getKey(), elapsed);
                // 取消响应式订阅，中断可能仍在等待的 blockLast()
                Optional.ofNullable(entry.getValue().getDisposable()).ifPresent(d -> {
                    if (!d.isDisposed())
                        d.dispose();
                });
                entry.getValue().getSink().tryEmitComplete();
            }
            return stale;
        });
    }

    /**
     * 任务信息
     */
    @Data
    public static class TaskInfo {
        /** SSE 输出通道 */
        private Sinks.Many<String> sink;
        /** 响应式订阅（用于中断） */
        private Disposable disposable;
        /** 任务启动时间 */
        private long startTime;
    }
}
