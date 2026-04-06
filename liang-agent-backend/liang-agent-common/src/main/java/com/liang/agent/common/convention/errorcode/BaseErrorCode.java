package com.liang.agent.common.convention.errorcode;

/**
 * 基础错误码定义枚举
 *
 * <p>
 * 定义了系统中常用的标准错误码，遵循阿里巴巴错误码规范：
 * <ul>
 *   <li>A 类错误：用户端错误（Client Error）</li>
 *   <li>B 类错误：系统执行错误（Service Error）</li>
 *   <li>C 类错误：第三方服务错误（Remote Error）</li>
 * </ul>
 * 通过组件包统一定义基础错误码，避免各服务重复定义相同内容。
 * </p>
 */
public enum BaseErrorCode implements IErrorCode {

    // ========== A 类错误：用户端错误 ==========

    /**
     * 一级宏观错误码：客户端错误
     */
    CLIENT_ERROR("A000001", "用户端错误"),

    // ========== B 类错误：系统执行错误 ==========

    /**
     * 一级宏观错误码：系统执行出错
     */
    SERVICE_ERROR("B000001", "系统执行出错"),

    /**
     * 二级宏观错误码：系统执行超时
     */
    SERVICE_TIMEOUT_ERROR("B000100", "系统执行超时"),

    // ========== C 类错误：第三方服务错误 ==========

    /**
     * 一级宏观错误码：调用第三方服务出错
     */
    REMOTE_ERROR("C000001", "调用第三方服务出错");

    /**
     * 错误码
     */
    private final String code;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 构造函数
     *
     * @param code    错误码
     * @param message 错误消息
     */
    BaseErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
