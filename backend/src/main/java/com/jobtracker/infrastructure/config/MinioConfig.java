package com.jobtracker.infrastructure.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class MinioConfig {

    @Value("${app.minio.endpoint}")   private String endpoint;
    @Value("${app.minio.access-key}") private String accessKey;
    @Value("${app.minio.secret-key}") private String secretKey;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();
        // The client's defaults are 5 minutes; an unreachable MinIO should fail fast,
        // both for uploads and for the health check.
        client.setTimeout(TimeUnit.SECONDS.toMillis(5), TimeUnit.SECONDS.toMillis(30),
            TimeUnit.SECONDS.toMillis(30));
        return client;
    }
}
