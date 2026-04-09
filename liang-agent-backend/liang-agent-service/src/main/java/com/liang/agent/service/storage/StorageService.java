package com.liang.agent.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件存储服务策略接口
 * <p>
 * 抽象文件存储操作，支持多种存储后端（MinIO、阿里云OSS、RustFS等）无缝替换。
 * 通过配置 {@code storage.type} 选择具体实现。
 * </p>
 */
public interface StorageService {

    /**
     * 上传 MultipartFile 文件
     *
     * @param file       上传的文件
     * @param objectName 对象存储路径/名称
     * @return 文件的公开访问 URL
     */
    String uploadFile(MultipartFile file, String objectName);

    /**
     * 上传字节数组
     *
     * @param objectName  对象存储路径/名称
     * @param content     文件字节内容
     * @param contentType MIME 类型
     * @return 文件的公开访问 URL
     */
    String uploadFile(String objectName, byte[] content, String contentType);

    /**
     * 下载文件
     *
     * @param objectName 对象存储路径/名称
     * @return 文件输入流
     */
    InputStream downloadFile(String objectName);

    /**
     * 删除文件
     *
     * @param objectName 对象存储路径/名称
     */
    void deleteFile(String objectName);
}
