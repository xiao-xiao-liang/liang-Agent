package com.liang.agent.service.file;

import com.liang.agent.model.entity.FileInfo;

import java.util.List;
import java.util.Optional;

/**
 * 文件信息 CRUD 服务接口
 */
public interface FileInfoService {

    /**
     * 保存文件信息
     *
     * @param fileInfo 文件信息实体
     */
    void saveFileInfo(FileInfo fileInfo);

    /**
     * 根据文件ID获取文件信息
     *
     * @param fileId 文件唯一标识
     * @return 文件信息
     */
    Optional<FileInfo> getByFileId(String fileId);

    /**
     * 根据文件ID更新文件信息
     *
     * @param fileInfo 文件信息实体（必须包含 fileId）
     */
    void updateByFileId(FileInfo fileInfo);

    /**
     * 根据文件ID删除文件信息
     *
     * @param fileId 文件唯一标识
     */
    void deleteByFileId(String fileId);

    /**
     * 检查文件是否存在
     *
     * @param fileId 文件唯一标识
     * @return 是否存在
     */
    boolean exists(String fileId);

    /**
     * 获取所有文件列表
     *
     * @return 文件列表
     */
    List<FileInfo> listAll();

    /**
     * 获取文件数量
     *
     * @return 文件数量
     */
    long count();
}
