package com.liang.agent.core.agent.deepresearch.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.liang.agent.core.agent.deepresearch.DeepResearchContext;
import com.liang.agent.core.prompt.DeepResearchPrompts;
import com.liang.agent.model.dto.PlanTask;
import com.liang.agent.model.dto.SearchResult;
import com.liang.agent.model.dto.TaskResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * 任务执行节点
 * <p>
 * 按 order 分组并行执行 PlanTask，每个任务内部运行 mini ReAct loop。
 * </p>
 */
@Slf4j
@Component
public class ExecuteNode implements NodeAction {

    /**
     * 子 ReAct loop 最大轮次
     */
    private static final int TASK_MAX_ROUNDS = 5;

    /**
     * 工具并发限流
     */
    private static final Semaphore TOOL_SEMAPHORE = new Semaphore(3);

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) throws Exception {
        DeepResearchContext ctx = DeepResearchContext.fromState(state);
        ctx.emitThinking("\n--- 开始执行任务 ---\n\n");

        List<PlanTask> planTasks = (List<PlanTask>) state.value("planTasks").orElse(Collections.emptyList());

        if (planTasks.isEmpty() || planTasks.stream().allMatch(t -> t.id() == null)) {
            ctx.emitThinking("无需执行任务，直接进入总结\n");
            return Map.of("taskResults", Collections.emptyMap());
        }

        Map<String, TaskResult> results = new ConcurrentHashMap<>();
        Map<String, String> accumulatedResults = new ConcurrentHashMap<>();

        Map<Integer, List<PlanTask>> grouped = planTasks.stream()
                .filter(t -> t.id() != null)
                .collect(Collectors.groupingBy(PlanTask::order));

        for (Integer order : new TreeSet<>(grouped.keySet())) {
            String dependencyContext = buildDependencyContext(accumulatedResults, planTasks, order);
            List<PlanTask> tasks = grouped.get(order);

            CountDownLatch latch = new CountDownLatch(tasks.size());

            for (PlanTask task : tasks) {
                Thread.startVirtualThread(() -> {
                    boolean acquired = false;
                    try {
                        TOOL_SEMAPHORE.acquire();
                        acquired = true;

                        TaskResult result = executeTask(task, dependencyContext, ctx);
                        results.put(task.id(), result);
                        if (result.success() && result.output() != null) {
                            accumulatedResults.put(task.id(), result.output());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        results.put(task.id(), new TaskResult(task.id(), false, null, "任务被中断"));
                    } catch (Exception e) {
                        log.error("Task {} 执行异常", task.id(), e);
                        results.put(task.id(), new TaskResult(task.id(), false, null, e.getMessage()));
                    } finally {
                        if (acquired) {
                            TOOL_SEMAPHORE.release();
                        }
                        latch.countDown();
                    }
                });
            }

            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("executePlan 被中断");
                break;
            }
        }

        ctx.emitThinking("\n--- 任务执行完成 ---\n\n");

        List<String> resultMessages = new ArrayList<>();
        for (Map.Entry<String, TaskResult> entry : results.entrySet()) {
            TaskResult result = entry.getValue();
            StringBuilder msg = new StringBuilder("【Completed Task Result】\n");
            msg.append("taskId: ").append(result.taskId()).append("\n");
            msg.append("success: ").append(result.success()).append("\n");
            if (result.output() != null) {
                msg.append("result:\n").append(result.output()).append("\n");
            }
            if (result.error() != null) {
                msg.append("error:\n").append(result.error()).append("\n");
            }
            msg.append("【End Task Result】");
            resultMessages.add(msg.toString());
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("taskResults", results);
        updates.put("messages", resultMessages);
        return updates;
    }

    // ==================== 单任务执行（mini ReAct loop） ====================

    private TaskResult executeTask(PlanTask task, String dependencyContext, DeepResearchContext ctx) {
        ctx.emitThinking("⚙️ 正在执行任务 " + task.id() + " : " + task.instruction() + "\n");

        try {
            String fullContext = """
                    【Available Results】
                    %s

                    【Current Task】
                    %s
                    """.formatted(dependencyContext, task.instruction());

            ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder()
                    .toolCallbacks(ctx.getToolCallbacks().toArray(new ToolCallback[0]))
                    .internalToolExecutionEnabled(false)
                    .build();

            ChatClient taskClient = ChatClient.builder(ctx.getChatModel())
                    .defaultOptions(toolOptions)
                    .defaultToolCallbacks(ctx.getToolCallbacks())
                    .build();

            List<Message> messages = Collections.synchronizedList(new ArrayList<>());
            messages.add(new SystemMessage(DeepResearchPrompts.getCurrentTime()));
            messages.add(new SystemMessage(DeepResearchPrompts.REACT_SYSTEM_PROMPT));
            messages.add(new SystemMessage(DeepResearchPrompts.EXECUTE));
            messages.add(new UserMessage("<question>" + fullContext + "</question>"));

            for (int round = 0; round < TASK_MAX_ROUNDS; round++) {
                ChatClientResponse chatResponse = taskClient.prompt()
                        .messages(messages)
                        .call()
                        .chatClientResponse();

                String aiText = chatResponse.chatResponse().getResult().getOutput().getText();

                if (!chatResponse.chatResponse().hasToolCalls()) {
                    ctx.getUsedTools().add("tavily_search");
                    ctx.emitThinking("执行结果: " + truncate(aiText, 200) + "\n\n");
                    return new TaskResult(task.id(), true, aiText, null);
                }

                List<AssistantMessage.ToolCall> toolCalls = chatResponse.chatResponse()
                        .getResult().getOutput().getToolCalls();

                messages.add(AssistantMessage.builder()
                        .content(aiText != null ? aiText : "")
                        .toolCalls(toolCalls)
                        .build());

                List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
                for (AssistantMessage.ToolCall toolCall : toolCalls) {
                    String toolName = toolCall.name();
                    String argsJson = toolCall.arguments();

                    emitSearchThinking(ctx, toolName, argsJson);

                    ToolCallback callback = findTool(ctx, toolName);
                    if (callback == null) {
                        toolResponses.add(new ToolResponseMessage.ToolResponse(
                                toolCall.id(), toolName, "{ \"error\": \"工具未找到：" + toolName + "\" }"));
                        continue;
                    }

                    try {
                        String result = callback.call(argsJson);
                        toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolName, result));
                        parseSearchResults(ctx, result);
                    } catch (Exception ex) {
                        toolResponses.add(new ToolResponseMessage.ToolResponse(
                                toolCall.id(), toolName, "{ \"error\": \"工具执行失败：" + ex.getMessage() + "\" }"));
                    }
                }

                messages.add(ToolResponseMessage.builder().responses(toolResponses).build());
            }

            messages.add(new UserMessage("你已达到最大推理轮次限制。请基于当前已有信息直接给出最终答案。禁止再调用任何工具。"));
            String finalAnswer = taskClient.prompt().messages(messages).call().content();
            return new TaskResult(task.id(), true, finalAnswer, null);

        } catch (Exception e) {
            log.error("Task {} 执行失败", task.id(), e);
            ctx.emitThinking("\n❌ 任务 " + task.id() + " 执行失败: " + e.getMessage() + "\n\n");
            return new TaskResult(task.id(), false, null, e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private String buildDependencyContext(Map<String, String> results, List<PlanTask> plan, int currentOrder) {
        if (currentOrder <= 1) {
            return "无\n";
        }
        StringBuilder context = new StringBuilder();
        boolean hasDep = false;
        for (Map.Entry<String, String> entry : results.entrySet()) {
            PlanTask task = plan.stream()
                    .filter(t -> t.id() != null && t.id().equals(entry.getKey()))
                    .findFirst().orElse(null);
            if (task != null && task.order() == currentOrder - 1) {
                if (!hasDep) {
                    context.append("任务 ");
                    hasDep = true;
                }
                context.append(String.format("%s: %s\n\n", entry.getKey(), entry.getValue()));
            }
        }
        return hasDep ? context.toString() : "无\n";
    }

    private ToolCallback findTool(DeepResearchContext ctx, String name) {
        return ctx.getToolCallbacks().stream()
                .filter(t -> t.getToolDefinition().name().equals(name))
                .findFirst().orElse(null);
    }

    private void emitSearchThinking(DeepResearchContext ctx, String toolName, String argsJson) {
        if (!toolName.contains("search")) {
            return;
        }
        try {
            JSONObject args = JSON.parseObject(argsJson);
            String query = args != null ? args.getString("query") : null;
            String text = (query != null && !query.isBlank())
                    ? "🔍 正在搜索信息: " + query + "\n"
                    : "🔍 正在搜索相关信息\n";
            ctx.emitThinking(text);
        } catch (Exception e) {
            ctx.emitThinking("🔍 正在搜索相关信息\n");
        }
    }

    private void parseSearchResults(DeepResearchContext ctx, String toolResult) {
        try {
            JSONObject responseJson = JSON.parseObject(toolResult);
            if (responseJson == null) {
                return;
            }
            var resultsArray = responseJson.getJSONArray("results");
            if (resultsArray != null) {
                for (int i = 0; i < resultsArray.size(); i++) {
                    JSONObject item = resultsArray.getJSONObject(i);
                    ctx.getAllSearchResults().add(new SearchResult(
                            item.getString("url"),
                            item.getString("title"),
                            item.getString("content")
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("搜索结果解析失败: {}", e.getMessage());
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
