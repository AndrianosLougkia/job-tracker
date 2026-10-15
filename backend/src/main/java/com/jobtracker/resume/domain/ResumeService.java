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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10 MB
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    /** Every PDF starts with this signature; the browser-supplied content type alone proves nothing. */
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_FILENAME_LENGTH = 255;

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

    /**
     * Extracted text of an owned resume, for other modules (e.g. analysis).
     * Returns null if PDF text extraction failed at upload time.
     */
    @Transactional(readOnly = true)
    public String getExtractedText(Long id, Long userId) {
        return findOwnedOrThrow(id, userId).getExtractedText();
    }

    // ------------------------------------------------------------------ //
    //  Commands                                                           //
    // ------------------------------------------------------------------ //

    public ResumeResponse upload(MultipartFile file, Long userId) throws IOException {
        validateFile(file);
        byte[] bytes = file.getBytes();
        if (!startsWithPdfSignature(bytes)) {
            throw new InvalidFileException("File is not a valid PDF");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> ResourceNotFoundException.user(userId));

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

        Resume resume = new Resume(user, displayFilename(file.getOriginalFilename()), storageKey,
            file.getSize(), extractedText);
        try {
            // saveAndFlush so a database failure surfaces here, while the object can still be removed
            return ResumeResponse.from(resumeRepository.saveAndFlush(resume));
        } catch (RuntimeException e) {
            storagePort.delete(storageKey);
            throw e;
        }
    }

    public void deleteResume(Long id, Long userId) {
        Resume resume = findOwnedOrThrow(id, userId);
        // Row first: if the database refuses, the file must still be there. A failed
        // storage delete only leaves an orphaned object, which the user never sees.
        resumeRepository.delete(resume);
        resumeRepository.flush();
        storagePort.delete(resume.getStorageKey());
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

    private static boolean startsWithPdfSignature(byte[] bytes) {
        return bytes.length >= PDF_MAGIC.length
            && Arrays.equals(bytes, 0, PDF_MAGIC.length, PDF_MAGIC, 0, PDF_MAGIC.length);
    }

    /**
     * Name shown to the user: browsers may send a full client path, and the column holds
     * at most 255 characters. The name is never used to build a storage path.
     */
    public static String displayFilename(String original) {
        if (original == null || original.isBlank()) return "resume.pdf";
        String name = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1)
            .replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) return "resume.pdf";
        return name.length() <= MAX_FILENAME_LENGTH ? name : name.substring(0, MAX_FILENAME_LENGTH);
    }

    private Resume findOwnedOrThrow(Long id, Long userId) {
        return resumeRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Resume not found: " + id));
    }
}
