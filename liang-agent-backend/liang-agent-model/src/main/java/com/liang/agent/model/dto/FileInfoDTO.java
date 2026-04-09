package com.liang.agent.model.dto;

import java.time.LocalDateTime;

/**
 * 文件信息 DTO
 * <p>
 * 用于 Controller 层返回给前端的视图对象，排除 extractedText 大字段以避免无意义的大量数据传输。
 * </p>
 *
 * @param fileId         文件唯一标识
 * @param fileName       原始文件名
 * @param fileType       文件类型
 * @param fileSize       文件大小（字节）
 * @param minioPath      MinIO中的存储路径
 * @param conversationId 关联会话ID
 * @param status         文件状态
 * @param embed          是否向量化（0:否 1:是）
 * @param createTime     创建时间
 */
public record FileInfoDTO(
        String fileId,
        String fileName,
        String fileType,
        Long fileSize,
        String minioPath,
        String conversationId,
        String status,
        Integer embed,
        LocalDateTime createTime
) {
}
