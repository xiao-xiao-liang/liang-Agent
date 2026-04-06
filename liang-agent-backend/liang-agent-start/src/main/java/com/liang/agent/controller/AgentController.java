package com.liang.agent.controller;

import com.liang.agent.common.convention.result.Result;
import com.liang.agent.common.convention.result.Results;
import com.liang.agent.core.agent.websearch.WebSearchReactAgent;
import com.liang.agent.model.dto.AgentChatRequest;
import com.liang.agent.service.message.ChatMessageService;
import com.liang.agent.service.conversation.ConversationService;
import com.liang.agent.service.task.AgentTaskManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Agent 控制器
 * <p>
 * 提供智能体流式对话接口。
 * 每次请求创建一个新的 Agent 实例，通过 SSE 流式返回结果。
 * </p>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final ChatModel chatModel;
    private final ConversationService conversationService;
    private final ChatMessageService chatMessageService;
    private final AgentTaskManager taskManager;
    private final List<ToolCallback> tavilyToolCallbacks;

    /**
     * WebSearch Agent 流式对话
     *
     * @param request 对话请求（包含 query 和 conversationId）
     * @return SSE 事件流
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody AgentChatRequest request) {
        String conversationId = request.conversationId();
        String query = request.query();

        // 如果调用方未传会话ID，自动生成一个全新的 UUID（方便接口测试）
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = java.util.UUID.randomUUID().toString().replace("-", "");
            log.info("未提供会话ID，自动生成新会话: conversationId={}", conversationId);
        }

        log.info("收到联网搜索请求: conversationId={}, queryLength={}", conversationId, query.length());

        WebSearchReactAgent agent = new WebSearchReactAgent(
                chatModel, conversationService, chatMessageService, taskManager, tavilyToolCallbacks);

        return agent.execute(conversationId, query);
    }

    /**
     * 停止当前会话的任务
     *
     * @param conversationId 会话ID
     * @return 操作结果
     */
    @PostMapping("/stop")
    public Result<Void> stop(@RequestParam("conversationId") @NotBlank(message = "会话ID不能为空") String conversationId) {
        log.info("收到停止请求: conversationId={}", conversationId);
        taskManager.stopTask(conversationId);
        return Results.success();
    }
}

