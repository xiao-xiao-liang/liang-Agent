package com.liang.agent.controller;

import com.liang.agent.common.convention.result.Result;
import com.liang.agent.common.convention.result.Results;
import com.liang.agent.model.vo.ConversationListVO;
import com.liang.agent.model.vo.PageResult;
import com.liang.agent.model.vo.ConversationDetailVO;
import com.liang.agent.service.conversation.ConversationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 会话管理控制器
 * <p>
 * 提供会话列表查询（分页）、会话详情查询、会话删除等接口。
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

    /**
     * 分页查询会话列表
     *
     * @param pageNum  页码（默认 1）
     * @param pageSize 每页大小（默认 10，最大 50）
     * @return 分页结果
     */
    @GetMapping("/list")
    public Result<PageResult<ConversationListVO>> listConversations(
            @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer pageSize) {
        return Results.success(conversationService.listConversations(pageNum, pageSize));
    }

    /**
     * 查询会话详情（会话元信息 + 所有消息记录）
     *
     * @param conversationId 会话ID
     * @return 会话详情
     */
    @GetMapping("/{conversationId}")
    public Result<ConversationDetailVO> getConversationDetail(
            @PathVariable @NotBlank(message = "会话ID不能为空") String conversationId) {
        return Results.success(conversationService.getConversationDetail(conversationId));
    }

    /**
     * 删除会话（逻辑删除，级联删除所有消息）
     *
     * @param conversationId 会话ID
     * @return 操作结果
     */
    @DeleteMapping("/{conversationId}")
    public Result<Void> deleteConversation(
            @PathVariable @NotBlank(message = "会话ID不能为空") String conversationId) {
        log.info("删除会话: conversationId={}", conversationId);
        conversationService.deleteConversation(conversationId);
        return Results.success();
    }
}
