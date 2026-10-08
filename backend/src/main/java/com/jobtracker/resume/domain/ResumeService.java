package com.jobtracker.resume.domain;

import com.jobtracker.infrastructure.exception.ResourceNotFoundException;
import com.jobtracker.resume.api.ResumeResponse;
import com.jobtracker.resume.infrastructure.PdfTextExtractor;
import com.jobtracker.resume.infrastructure.StoragePort;
import com.jobtracker.user.domain.User;
import com.jobtracker.user.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10 MB
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final StoragePort storagePort;
    private final PdfTextExtractor pdfTextExtractor;

    public ResumeService(ResumeRepository resumeRepository,
                         UserRepository userRepository,
                         StoragePort storagePort,
                         PdfTextExtractor pdfTextExtractor) {
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.storagePort = storagePort;
        this.pdfTextExtractor = pdfTextExtractor;
    }

    // ------------------------------------------------------------------ //
    //  Queries                                                            //
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public List<ResumeResponse> listResumes(Long userId) {
        return resumeRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream().map(ResumeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ResumeResponse getResume(Long id, Long userId) {
        return ResumeResponse.from(findOwnedOrThrow(id, userId));
    }

    // ------------------------------------------------------------------ //
    //  Commands                                                           //
    // ------------------------------------------------------------------ //

    public ResumeResponse upload(MultipartFile file, Long userId) throws IOException {
        validateFile(file);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> ResourceNotFoundException.user(userId));

        byte[] bytes = file.getBytes();
        String storageKey = "resumes/" + userId + "/" + UUID.randomUUID() + ".pdf";

        // Store in MinIO first
        storagePort.store(storageKey, bytes, PDF_CONTENT_TYPE);

        // Extract text — failure is non-fatal
        String extractedText = null;
        try {
            extractedText = pdfTextExtractor.extract(bytes);
        } catch (Exception e) {
            log.warn("Text extraction failed for {}: {}", storageKey, e.getMessage());
        }

        Resume resume = new Resume(user, file.getOriginalFilename(), storageKey,
            file.getSize(), extractedText);

        return ResumeResponse.from(resumeRepository.save(resume));
    }

    public void deleteResume(Long id, Long userId) {
        Resume resume = findOwnedOrThrow(id, userId);
        // Delete from storage first; DB row removed after
        storagePort.delete(resume.getStorageKey());
        resumeRepository.delete(resume);
    }

    // ------------------------------------------------------------------ //
    //  Internal helpers                                                   //
    // ------------------------------------------------------------------ //

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidFileException("File must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileTooLargeException("File exceeds the 10 MB limit");
        }
        String contentType = file.getContentType();
        if (!PDF_CONTENT_TYPE.equals(contentType)) {
            throw new InvalidFileException("Only PDF files are accepted");
        }
    }

    private Resume findOwnedOrThrow(Long id, Long userId) {
        return resumeRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Resume not found: " + id));
    }
}
