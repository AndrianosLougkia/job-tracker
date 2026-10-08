package com.jobtracker.resume.domain;

import com.jobtracker.user.domain.User;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * Resume metadata row.
 * The actual PDF bytes live in MinIO under storageKey.
 * extractedText is null if PDFBox failed — the upload still succeeds.
 */
@Entity
@Table(name = "resumes")
public class Resume {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "storage_key", nullable = false, unique = true, length = 512)
    private String storageKey;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Resume() {}

    public Resume(User user, String originalFilename, String storageKey,
                  Long fileSizeBytes, String extractedText) {
        this.user = user;
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.fileSizeBytes = fileSizeBytes;
        this.extractedText = extractedText;
    }

    @PrePersist void onCreate() { createdAt = Instant.now(); }

    public Long getId()               { return id; }
    public User getUser()             { return user; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStorageKey()     { return storageKey; }
    public Long getFileSizeBytes()    { return fileSizeBytes; }
    public String getExtractedText()  { return extractedText; }
    public Instant getCreatedAt()     { return createdAt; }
}
