package com.jobtracker.infrastructure.web;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

/**
 * Reports resume storage in {@code /actuator/health} as component {@code minio}.
 * Without MinIO, uploads fail with 503 while everything else keeps working, so
 * DOWN here points straight at the cause. Disable with {@code management.health.minio.enabled=false}.
 */
@Component("minio")
@ConditionalOnEnabledHealthIndicator("minio")
public class MinioHealthIndicator extends AbstractHealthIndicator {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioHealthIndicator(MinioClient minioClient, @Value("${app.minio.bucket}") String bucket) {
        super("MinIO health check failed");
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        // bucketExists is false until the first upload creates it; either answer means MinIO is up
        minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        builder.up();
    }
}
