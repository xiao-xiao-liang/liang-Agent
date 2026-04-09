package com.liang.agent.config;

import com.liang.agent.service.storage.MinioProperties;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置
 * <p>
 * 当 {@code storage.type=minio}（或未配置时默认激活）时创建 MinioClient Bean。
 * </p>
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.type", havingValue = "minio", matchIfMissing = true)
public class MinioConfig {

    private final MinioProperties minioProperties;

    /**
     * 创建 MinIO 客户端 Bean
     *
     * @return MinioClient 实例
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }
}
