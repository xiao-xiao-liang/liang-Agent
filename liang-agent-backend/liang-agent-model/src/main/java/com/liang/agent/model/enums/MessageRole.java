package com.liang.agent.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 消息角色枚举
 * <p>
 * 标识会话中每条消息的发送方：
 * <ul>
 *   <li>{@link #USER} — 用户提问</li>
 *   <li>{@link #ASSISTANT} — AI 回答</li>
 *   <li>{@link #SYSTEM} — 系统指令（预留）</li>
 * </ul>
 * 使用 {@link EnumValue} 标注数据库存储值，MyBatis-Plus 自动完成 String ↔ Enum 转换。
 * </p>
 */
@Getter
public enum MessageRole {

    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system");

    /**
     * 数据库存储值
     */
    @EnumValue
    private final String value;

    MessageRole(String value) {
        this.value = value;
    }
}
