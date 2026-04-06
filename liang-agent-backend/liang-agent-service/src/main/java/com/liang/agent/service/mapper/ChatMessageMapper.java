package com.liang.agent.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.agent.model.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话消息 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
