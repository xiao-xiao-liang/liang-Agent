package com.liang.agent.service.file;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.agent.model.entity.FileInfo;
import com.liang.agent.service.mapper.FileInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 文件信息服务实现类
 * <p>
 * 基于 MyBatis-Plus 链式编程简化 CRUD 操作。
 * 时间字段由 {@code MyBatisMetaObjectHandler} 自动填充，
 * 逻辑删除由 {@code @TableLogic} 自动处理。
 * </p>
 */
@Slf4j
@Service
public class FileInfoServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo> implements FileInfoService {

    @Override
    public void saveFileInfo(FileInfo fileInfo) {
        save(fileInfo);
        log.info("文件信息已保存: fileId={}", fileInfo.getFileId());
    }

    @Override
    public Optional<FileInfo> getByFileId(String fileId) {
        return lambdaQuery()
                .eq(FileInfo::getFileId, fileId)
                .oneOpt();
    }

    @Override
    public void updateByFileId(FileInfo fileInfo) {
        boolean success = lambdaUpdate()
                .eq(FileInfo::getFileId, fileInfo.getFileId())
                .update(fileInfo);
        log.debug("文件信息已更新: fileId={}, success={}", fileInfo.getFileId(), success);
    }

    @Override
    public void deleteByFileId(String fileId) {
        lambdaUpdate()
                .eq(FileInfo::getFileId, fileId)
                .remove();
        log.info("文件信息已删除（逻辑）: fileId={}", fileId);
    }

    @Override
    public boolean exists(String fileId) {
        return lambdaQuery()
                .eq(FileInfo::getFileId, fileId)
                .exists();
    }

    @Override
    public List<FileInfo> listAll() {
        return lambdaQuery().list();
    }

    @Override
    public long count() {
        return lambdaQuery().count();
    }
}
