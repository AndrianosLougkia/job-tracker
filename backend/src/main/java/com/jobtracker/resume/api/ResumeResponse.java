package com.jobtracker.resume.api;

import com.jobtracker.resume.domain.Resume;
import java.time.Instant;

public class ResumeResponse {

    private Long id;
    private Long userId;
    private String originalFilename;
    private Long fileSizeBytes;
    private boolean textExtracted;
    private Instant createdAt;

    public static ResumeResponse from(Resume r) {
        ResumeResponse dto = new ResumeResponse();
        dto.id = r.getId();
        dto.userId = r.getUser().getId();
        dto.originalFilename = r.getOriginalFilename();
        dto.fileSizeBytes = r.getFileSizeBytes();
        dto.textExtracted = r.getExtractedText() != null;
        dto.createdAt = r.getCreatedAt();
        return dto;
    }

    public Long getId()               { return id; }
    public Long getUserId()           { return userId; }
    public String getOriginalFilename() { return originalFilename; }
    public Long getFileSizeBytes()    { return fileSizeBytes; }
    public boolean isTextExtracted()  { return textExtracted; }
    public Instant getCreatedAt()     { return createdAt; }
}
