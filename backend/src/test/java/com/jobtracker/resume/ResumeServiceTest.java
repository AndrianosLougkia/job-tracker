package com.jobtracker.resume;

import com.jobtracker.infrastructure.exception.ResourceNotFoundException;
import com.jobtracker.resume.domain.*;
import com.jobtracker.resume.infrastructure.PdfTextExtractor;
import com.jobtracker.resume.infrastructure.StoragePort;
import com.jobtracker.user.domain.User;
import com.jobtracker.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock ResumeRepository resumeRepository;
    @Mock UserRepository userRepository;
    @Mock StoragePort storagePort;
    @Mock PdfTextExtractor pdfTextExtractor;

    @InjectMocks ResumeService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("test@example.com", "hash");
        setId(user, 1L);
    }

    @Test
    void uploadRejectsNonPdfFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "cv.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "content".getBytes());

        assertThatThrownBy(() -> service.upload(file, 1L))
            .isInstanceOf(InvalidFileException.class)
            .hasMessageContaining("PDF");
    }

    @Test
    void uploadRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.upload(file, 1L))
            .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void uploadStoresFileAndPersistsResume() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.pdf", "application/pdf", "pdf content".getBytes());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(pdfTextExtractor.extract(any())).thenReturn("Extracted text");

        Resume saved = new Resume(user, "resume.pdf", "resumes/1/uuid.pdf", 11L, "Extracted text");
        setId(saved, 10L);
        when(resumeRepository.save(any())).thenReturn(saved);

        var response = service.upload(file, 1L);

        assertThat(response.getOriginalFilename()).isEqualTo("resume.pdf");
        assertThat(response.isTextExtracted()).isTrue();
        verify(storagePort).store(anyString(), any(), eq("application/pdf"));
        verify(resumeRepository).save(any());
    }

    @Test
    void uploadSucceedsEvenWhenExtractionFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.pdf", "application/pdf", "pdf content".getBytes());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(pdfTextExtractor.extract(any())).thenReturn(null);

        Resume saved = new Resume(user, "resume.pdf", "resumes/1/uuid.pdf", 11L, null);
        setId(saved, 10L);
        when(resumeRepository.save(any())).thenReturn(saved);

        var response = service.upload(file, 1L);

        assertThat(response.isTextExtracted()).isFalse();
        verify(storagePort).store(anyString(), any(), any());
    }

    @Test
    void deleteRemovesFromStorageAndDatabase() {
        Resume resume = new Resume(user, "resume.pdf", "resumes/1/abc.pdf", 100L, null);
        setId(resume, 5L);
        when(resumeRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(resume));

        service.deleteResume(5L, 1L);

        verify(storagePort).delete("resumes/1/abc.pdf");
        verify(resumeRepository).delete(resume);
    }

    @Test
    void deleteThrowsForWrongUser() {
        when(resumeRepository.findByIdAndUserId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteResume(1L, 99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
