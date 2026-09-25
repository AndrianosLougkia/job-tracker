package com.jobtracker.application.domain;

import com.jobtracker.AbstractIntegrationTest;
import com.jobtracker.user.domain.User;
import com.jobtracker.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-layer tests.
 * Uses @DataJpaTest (lightweight Spring context — only JPA/repositories loaded)
 * with a real Testcontainers PostgreSQL instance so Flyway and the ENUM type work.
 */
@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
class ApplicationRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jobtracker_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired ApplicationRepository applicationRepository;
    @Autowired UserRepository userRepository;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        userRepository.deleteAll();

        userA = userRepository.save(new User("a@example.com", "hash"));
        userB = userRepository.save(new User("b@example.com", "hash"));
    }

    @Test
    void savesAndRetrievesApplication() {
        JobApplication app = new JobApplication(userA, "Acme Corp", "Backend Engineer");
        app.setJobDescription("Build things");
        applicationRepository.save(app);

        Optional<JobApplication> found = applicationRepository.findByIdAndUserId(app.getId(), userA.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getCompany()).isEqualTo("Acme Corp");
        assertThat(found.get().getStatus()).isEqualTo(ApplicationStatus.WISHLIST);
    }

    @Test
    void findsOnlyApplicationsForGivenUser() {
        applicationRepository.save(new JobApplication(userA, "Acme", "Dev"));
        applicationRepository.save(new JobApplication(userA, "Baz", "QA"));
        applicationRepository.save(new JobApplication(userB, "Other Corp", "PM"));

        List<JobApplication> userAApps = applicationRepository
            .findByUserIdOrderByCreatedAtDesc(userA.getId());

        assertThat(userAApps).hasSize(2);
        assertThat(userAApps).extracting(a -> a.getUser().getId())
            .containsOnly(userA.getId());
    }

    @Test
    void doesNotReturnApplicationBelongingToAnotherUser() {
        JobApplication app = applicationRepository.save(
            new JobApplication(userA, "Acme", "Dev"));

        Optional<JobApplication> result = applicationRepository
            .findByIdAndUserId(app.getId(), userB.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void countsApplicationsByStatus() {
        JobApplication a1 = new JobApplication(userA, "A", "Dev");
        a1.setStatus(ApplicationStatus.APPLIED);
        JobApplication a2 = new JobApplication(userA, "B", "Dev");
        a2.setStatus(ApplicationStatus.APPLIED);
        JobApplication a3 = new JobApplication(userA, "C", "Dev");
        a3.setStatus(ApplicationStatus.INTERVIEW);
        applicationRepository.saveAll(List.of(a1, a2, a3));

        assertThat(applicationRepository.countByUserIdAndStatus(userA.getId(), ApplicationStatus.APPLIED)).isEqualTo(2);
        assertThat(applicationRepository.countByUserIdAndStatus(userA.getId(), ApplicationStatus.INTERVIEW)).isEqualTo(1);
        assertThat(applicationRepository.countByUserIdAndStatus(userB.getId(), ApplicationStatus.APPLIED)).isEqualTo(0);
    }
}
