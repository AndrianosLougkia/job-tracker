package com.jobtracker.resume;

import com.jobtracker.AbstractIntegrationTest;
import com.jobtracker.TestJwtHelper;
import com.jobtracker.resume.domain.ResumeRepository;
import com.jobtracker.user.domain.User;
import com.jobtracker.user.domain.UserRepository;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Resume upload and delete against a real MinIO AIStor, the same image as Docker Compose.
 *
 * AIStor only starts with a license file and the community MinIO images are no longer
 * published, so this test runs only when {@code minio.license} is in the project root
 * (next to docker-compose.yml) and is skipped otherwise.
 */
@AutoConfigureMockMvc
@EnabledIf("licensePresent")
class MinioStorageIntegrationTest extends AbstractIntegrationTest {

    static final String MINIO_IMAGE = "quay.io/minio/aistor/minio:RELEASE.2026-09-19T17-05-25Z";
    static final Path LICENSE = Path.of("..", "minio.license");

    static boolean licensePresent() {
        return Files.isRegularFile(LICENSE);
    }

    /** Started on first use, so nothing is pulled or started when the test is skipped. */
    private static final class Minio {
        static final GenericContainer<?> CONTAINER = new GenericContainer<>(MINIO_IMAGE)
            .withCopyFileToContainer(MountableFile.forHostPath(LICENSE), "/minio.license")
            .withEnv("MINIO_ROOT_USER", "testadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "testadmin-secret")
            .withCommand("server", "/data", "--license", "/minio.license")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

        static {
            CONTAINER.start();
        }

        static String endpoint() {
            return "http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(9000);
        }
    }

    @DynamicPropertySource
    static void registerMinio(DynamicPropertyRegistry registry) {
        registry.add("app.minio.endpoint", Minio::endpoint);
        registry.add("app.minio.access-key", () -> "testadmin");
        registry.add("app.minio.secret-key", () -> "testadmin-secret");
        registry.add("management.health.minio.enabled", () -> "true");
    }

    @Autowired MockMvc mockMvc;
    @Autowired MinioClient minioClient;
    @Autowired ResumeRepository resumeRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestJwtHelper jwtHelper;
    @Value("${app.minio.bucket}") String bucket;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("minio-" + UUID.randomUUID() + "@example.com", "hash"));
    }

    @Test
    void uploadStoresThePdfInMinioAndExtractsItsText() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf",
            pdfWithText("Kotlin and Spring Boot engineer"));

        String json = mockMvc.perform(multipart("/resumes").file(file)
                .header("Authorization", jwtHelper.authHeader(user)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.textExtracted").value(true))
            .andReturn().getResponse().getContentAsString();

        var resume = resumeRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).getFirst();
        assertThat(json).contains("\"id\":" + resume.getId());
        assertThat(resume.getExtractedText()).contains("Kotlin and Spring Boot engineer");
        var stat = minioClient.statObject(StatObjectArgs.builder()
            .bucket(bucket).object(resume.getStorageKey()).build());
        assertThat(stat.size()).isEqualTo(file.getSize());
        assertThat(stat.contentType()).isEqualTo("application/pdf");
        assertThat(resume.getStorageKey()).startsWith("resumes/" + user.getId() + "/");
    }

    @Test
    void deleteRemovesTheObjectFromMinio() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf",
            pdfWithText("Resume to delete"));
        mockMvc.perform(multipart("/resumes").file(file).header("Authorization", jwtHelper.authHeader(user)))
            .andExpect(status().isCreated());
        var resume = resumeRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).getFirst();

        mockMvc.perform(delete("/resumes/" + resume.getId()).header("Authorization", jwtHelper.authHeader(user)))
            .andExpect(status().isNoContent());

        assertThat(resumeRepository.findById(resume.getId())).isEmpty();
        assertThatThrownBy(() -> minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucket).object(resume.getStorageKey()).build()))
            .isInstanceOf(ErrorResponseException.class);
    }

    @Test
    void healthReportsMinioUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.minio.status").value("UP"));
    }

    private static byte[] pdfWithText(String text) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText(text);
                content.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
