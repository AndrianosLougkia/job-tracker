package com.jobtracker.resume.infrastructure;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

/**
 * MinIO-backed implementation of StoragePort.
 * All MinIO-specific imports are confined to this class.
 */
@Service
public class MinioStorageService implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioClient minioClient;
    private final String bucket;

    public MinioStorageService(MinioClient minioClient,
                                @Value("${app.minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public void store(String key, byte[] bytes, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                .contentType(contentType)
                .build());
            log.debug("Stored object: {}/{}", bucket, key);
        } catch (Exception e) {
            log.error("Failed to store object {}: {}", key, e.getMessage());
            throw new StorageException("Failed to upload file to storage", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build());
            log.debug("Deleted object: {}/{}", bucket, key);
        } catch (Exception e) {
            // Log but do not propagate — deletion failure should not block the user
            log.warn("Failed to delete object {}: {}", key, e.getMessage());
        }
    }
}
