package com.liang.agent.common.response;

import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent SSE 流式输出统一格式
 * <p>
 * 前端通过 EventSource 接收此格式的 JSON 字符串，根据 type 字段决定渲染策略：
 * <ul>
 *   <li>text    → 正文内容（逐字追加）</li>
 *   <li>thinking → 思考过程（折叠展示）</li>
 *   <li>reference → 搜索引用（卡片展示）</li>
 *   <li>recommend → 推荐问题（按钮展示）</li>
 *   <li>error   → 错误信息（Toast 提示）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    /** 消息类型 */
    private String type;

    /** 消息内容（String 或 List，根据 type 决定） */
    private Object content;

    /** 引用数量（仅 reference 类型使用） */
    private Integer count;

    public static String text(String content) {
        return JSON.toJSONString(AgentResponse.builder().type("text").content(content).build());
    }

    public static String thinking(String content) {
        return JSON.toJSONString(AgentResponse.builder().type("thinking").content(content).build());
    }

    public static String reference(String content, int count) {
        return JSON.toJSONString(AgentResponse.builder().type("reference").content(content).count(count).build());
    }

    public static String recommend(List<String> questions) {
        return JSON.toJSONString(AgentResponse.builder().type("recommend").content(questions).build());
    }

    public static String error(String content) {
        return JSON.toJSONString(AgentResponse.builder().type("error").content(content).build());
    }
}
