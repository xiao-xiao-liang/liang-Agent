package com.liang.agent.model.enums;

/**
 * 文件处理状态枚举
 */
public enum FileStatus {

    /**
     * 待处理
     */
    PENDING,

    /**
     * 处理中
     */
    PROCESSING,

    /**
     * 处理成功
     */
    SUCCESS,

    /**
     * 处理失败
     */
    FAILED
}
