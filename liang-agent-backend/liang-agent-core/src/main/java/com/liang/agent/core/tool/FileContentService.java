package com.liang.agent.core.tool;

import com.liang.agent.model.entity.FileInfo;
import com.liang.agent.model.enums.FileStatus;
import com.liang.agent.service.embedding.VectorStoreService;
import com.liang.agent.service.file.FileManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 文件内容加载工具
 * <p>
 * 供 FileReactAgent 调用的 {@code @Tool} 工具，根据文件的 embed 字段自动选择加载方式：
 * <ul>
 *   <li>embed=1 → 使用 RAG 语义检索（适用于大文件）</li>
 *   <li>embed=0/null → 直接加载完整文件内容（适用于小文件）</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileContentService {

    private final FileManageService fileManageService;
    private final VectorStoreService vectorStoreService;

    /**
     * 加载文件内容或进行RAG检索
     *
     * @param fileId   文件ID
     * @param question 用户问题（用于RAG检索）
     * @return 文件信息或检索结果
     */
    @Tool(description = "根据文件ID加载文件内容或进行RAG语义检索。如果文件已向量化(embed=1)则使用语义搜索返回相关片段，否则直接返回完整文件内容。")
    public String loadContent(
            @ToolParam(description = "文件ID") String fileId,
            @ToolParam(description = "用户的问题，用于语义检索（可选）") String question) {
        log.info("EXECUTE Tool: loadContent: fileId={}, question={}", fileId, question);

        if (fileId == null || fileId.trim().isEmpty())
            return "文件ID不能为空";

        try {
            // 查询文件信息
            FileInfo fileInfo = fileManageService.getFileInfo(fileId);

            // 检查文件处理状态
            if (!FileStatus.SUCCESS.name().equals(fileInfo.getStatus()))
                return String.format("文件处理中或处理失败，当前状态: %s，文件ID: %s", fileInfo.getStatus(), fileId);

            // 根据 embed 字段选择加载方式
            Integer embed = fileInfo.getEmbed();
            if (embed != null && embed == 1)
                return retrieveWithRag(fileId, fileInfo, question);
            else
                return loadDirectly(fileId, fileInfo);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("加载文件内容失败: fileId={}, question={}", fileId, question, e);
            return "加载文件内容失败: " + e.getMessage();
        }
    }

    /**
     * 使用RAG语义检索方式加载文件内容
     */
    private String retrieveWithRag(String fileId, FileInfo fileInfo, String question) {
        if (question == null || question.trim().isEmpty())
            return buildResponse(fileInfo, "请提供具体问题以进行语义检索。", null);

        List<String> results = vectorStoreService.ragRetrieve(fileId, question);

        if (results == null || results.isEmpty())
            return buildResponse(fileInfo, "未检索到与问题相关的内容", null);

        return buildResponse(fileInfo, "RAG检索", results);
    }

    /**
     * 直接加载完整文件内容
     */
    private String loadDirectly(String fileId, FileInfo fileInfo) {
        String content = fileManageService.getFileContent(fileId);
        String contentText = (content != null && !content.trim().isEmpty()) ? content : "该文件没有可识别的内容";
        return buildResponse(fileInfo, contentText, null);
    }

    /**
     * 统一构建响应格式
     *
     * @param fileInfo 文件信息
     * @param content  内容或检索结果
     * @param segments 检索片段列表（RAG模式）
     * @return 统一格式的响应字符串
     */
    private String buildResponse(FileInfo fileInfo, String content, List<String> segments) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 文件信息 ===\n");
        sb.append("文件名: ").append(fileInfo.getFileName()).append("\n");
        sb.append("文件类型: ").append(fileInfo.getFileType()).append("\n");

        sb.append("\n=== 文件内容 ===\n");

        if (segments != null && !segments.isEmpty()) {
            sb.append("相关内容: \n\n");
            for (String segment : segments)
                sb.append(segment).append("\n\n");
        } else {
            sb.append(Objects.requireNonNullElse(content, "无内容可显示"));
        }

        return sb.toString();
    }
}
