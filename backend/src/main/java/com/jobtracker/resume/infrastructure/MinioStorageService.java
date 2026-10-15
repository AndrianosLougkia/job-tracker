package com.jobtracker.resume.infrastructure;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

/**
 * MinIO-backed implementation of StoragePort.
 * All MinIO-specific imports are confined to this class.
 *
 * The bucket is created on startup, or on the first upload if MinIO was not reachable
 * then, so the backend can start before MinIO and still work once it is up.
 */
@Service
public class MinioStorageService implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioClient minioClient;
    private final String bucket;
    private volatile boolean bucketReady;

    public MinioStorageService(MinioClient minioClient,
                                @Value("${app.minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @EventListener(ApplicationReadyEvent.class)
    void prepareBucketOnStartup() {
        try {
            ensureBucket();
        } catch (Exception e) {
            log.warn("MinIO is not reachable yet; bucket '{}' will be created on the first upload: {}",
                bucket, e.getMessage());
        }
    }

    @Override
    public void store(String key, byte[] bytes, String contentType) {
        try {
            ensureBucket();
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

    private void ensureBucket() throws Exception {
        if (bucketReady) return;
        synchronized (this) {
            if (bucketReady) return;
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
            bucketReady = true;
        }
    }
}
