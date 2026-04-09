package com.liang.agent.service.storage;

import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * MinIO 文件存储服务实现
 * <p>
 * 当配置 {@code storage.type=minio}（或未配置时默认激活）时生效。
 * 负责与 MinIO 服务端交互，完成文件的上传、下载和删除。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.type", havingValue = "minio", matchIfMissing = true)
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Override
    public String uploadFile(MultipartFile file, String objectName) {
        try {
            createBucketIfNotExists();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1L)
                    .contentType(file.getContentType())
                    .build());

            String url = buildAccessUrl(objectName);
            log.info("MinIO 文件上传成功: objectName={}, url={}", objectName, url);
            return url;
        } catch (Exception e) {
            log.error("MinIO 文件上传失败: objectName={}", objectName, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadFile(String objectName, byte[] content, String contentType) {
        try {
            createBucketIfNotExists();
            try (InputStream stream = new ByteArrayInputStream(content)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioProperties.getBucketName())
                        .object(objectName)
                        .stream(stream, (long) content.length, -1L)
                        .contentType(contentType)
                        .build());
            }

            String url = buildAccessUrl(objectName);
            log.info("MinIO 字节数组上传成功: objectName={}", objectName);
            return url;
        } catch (Exception e) {
            log.error("MinIO 字节数组上传失败: objectName={}", objectName, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream downloadFile(String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 文件下载失败: objectName={}", objectName, e);
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .build());
            log.info("MinIO 文件删除成功: objectName={}", objectName);
        } catch (Exception e) {
            log.error("MinIO 文件删除失败: objectName={}", objectName, e);
            throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
        }
    }

    /**
     * 确保 Bucket 存在，不存在则自动创建并设置公共读策略
     */
    private void createBucketIfNotExists() throws Exception {
        String bucketName = minioProperties.getBucketName();
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

            // 设置 Bucket 策略为公共读
            String policy = """
                    {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}
                    """.formatted(bucketName);
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucketName)
                    .config(policy)
                    .build());
            log.info("MinIO Bucket 创建成功: bucketName={}", bucketName);
        }
    }

    /**
     * 构建文件公开访问 URL
     */
    private String buildAccessUrl(String objectName) {
        String endpoint = minioProperties.getEndpoint();
        String cleanEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return String.format("%s/%s/%s", cleanEndpoint, minioProperties.getBucketName(), objectName);
    }
}
