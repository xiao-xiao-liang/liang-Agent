package com.liang.agent.controller;

import com.liang.agent.common.convention.result.Result;
import com.liang.agent.common.convention.result.Results;
import com.liang.agent.model.vo.ConversationListVO;
import com.liang.agent.model.vo.MessageVO;
import com.liang.agent.service.message.ChatMessageService;
import com.liang.agent.service.conversation.ConversationService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会话管理控制器
 * <p>
 * 提供会话列表查询、会话详情查询、会话删除等接口。
 * Controller 仅做参数接收 → Service 调用 → 结果返回的薄编排层。
 * </p>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/conversation")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ChatMessageService chatMessageService;

    /**
     * 查询会话列表（按最近消息时间倒序）
     *
     * @return 会话列表
     */
    @GetMapping("/list")
    public Result<List<ConversationListVO>> listConversations() {
        return Results.success(conversationService.listConversations());
    }

    /**
     * 查询会话详情（所有消息记录）
     *
     * @param conversationId 会话ID
     * @return 消息列表（按时间正序）
     */
    @GetMapping("/{conversationId}")
    public Result<List<MessageVO>> getConversationDetail(@PathVariable @NotBlank(message = "会话ID不能为空") String conversationId) {
        return Results.success(chatMessageService.getMessagesByConversationId(conversationId));
    }

    /**
     * 删除会话（逻辑删除，级联删除所有消息）
     *
     * @param conversationId 会话ID
     * @return 操作结果
     */
    @DeleteMapping("/{conversationId}")
    public Result<Void> deleteConversation(@PathVariable @NotBlank(message = "会话ID不能为空") String conversationId) {
        log.info("删除会话: conversationId={}", conversationId);
        conversationService.deleteConversation(conversationId);
        return Results.success();
    }
}
