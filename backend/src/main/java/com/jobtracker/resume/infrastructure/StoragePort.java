package com.jobtracker.resume.infrastructure;

/**
 * Abstraction over object storage.
 * The service layer depends on this interface — MinIO is an implementation detail.
 */
public interface StoragePort {

    /**
     * Upload bytes under the given key.
     * @param key    unique object key (e.g. "resumes/{userId}/{uuid}.pdf")
     * @param bytes  raw file bytes
     * @param contentType MIME type
     */
    void store(String key, byte[] bytes, String contentType);

    /**
     * Delete an object by key.
     * Silently succeeds if the object does not exist.
     */
    void delete(String key);
}
