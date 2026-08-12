package com.ruwei.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储配置，读取 application.yml 中的 minio.* 配置项
 */
@Configuration
@ConfigurationProperties(prefix = "minio")
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "minio")
@Data
public class MinioClientConfig {

    /**
     * 服务地址（注意：必须是 API 端口 9000，例如 http://192.168.109.128:9000）
     */
    private String endpoint;

    /**
     * 访问账号
     */
    private String accessKey;

    /**
     * 访问密钥（注意不要泄露）
     */
    private String secretKey;

    /**
     * 桶名
     */
    private String bucketName;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
