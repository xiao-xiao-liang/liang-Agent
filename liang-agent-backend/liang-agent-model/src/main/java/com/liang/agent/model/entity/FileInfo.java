package com.liang.agent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件元数据实体
 * <p>
 * 对应数据库表 file_info，存储文件基本信息和解析后的内容。
 * </p>
 *
 * @author liang
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("file_info")
public class FileInfo {

    /**
     * 主键ID（自增）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 文件唯一标识
     */
    private String fileId;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件类型（pdf/doc/docx/txt/png/jpg等）
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * MinIO中的存储路径
     */
    private String minioPath;

    /**
     * 解析后的纯文本内容
     */
    private String extractedText;

    /**
     * 会话ID（可选，用于关联特定会话）
     */
    private String conversationId;

    /**
     * 文件状态：PENDING/PROCESSING/SUCCESS/FAILED
     */
    private String status;

    /**
     * 是否向量化 0:否 1:是
     */
    private Integer embed;

    /**
     * 创建时间（自动填充）
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间（自动填充）
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除 0:正常 1:删除
     */
    @TableLogic
    private Integer deleted;
}
