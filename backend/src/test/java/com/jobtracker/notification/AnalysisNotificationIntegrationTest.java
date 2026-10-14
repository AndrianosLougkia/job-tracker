package com.jobtracker.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.AbstractIntegrationTest;
import com.jobtracker.TestJwtHelper;
import com.jobtracker.ai.domain.AiProviderException;
import com.jobtracker.ai.infrastructure.MockAiProvider;
import com.jobtracker.analysis.domain.AnalysisRepository;
import com.jobtracker.application.domain.ApplicationRepository;
import com.jobtracker.application.domain.JobApplication;
import com.jobtracker.notification.domain.AnalysisNotification;
import com.jobtracker.resume.domain.Resume;
import com.jobtracker.resume.domain.ResumeRepository;
import com.jobtracker.user.domain.User;
import com.jobtracker.user.domain.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real STOMP clients against the running server: notifications reach the owner of an
 * analysis, never another user, and unauthenticated or out-of-bounds frames are refused.
 */
@AutoConfigureMockMvc
class AnalysisNotificationIntegrationTest extends AbstractIntegrationTest {

    private static final String QUEUE = "/user/queue/analyses";
    private static final String JOB = "We need Java, Spring Boot, Kubernetes, PostgreSQL and AWS experience.";
    private static final String RESUME_TEXT = "Backend engineer: Java, Spring Boot, PostgreSQL and Docker.";

    @LocalServerPort int port;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestJwtHelper jwtHelper;
    @Autowired AnalysisRepository analysisRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired ResumeRepository resumeRepository;
    @Autowired UserRepository userRepository;
    @SpyBean MockAiProvider mockAiProvider;

    private WebSocketStompClient stompClient;
    private final List<StompSession> sessions = new ArrayList<>();
    private User owner;
    private Long applicationId;
    private Long resumeId;

    @BeforeEach
    void setUp() {
        analysisRepository.deleteAll();
        resumeRepository.deleteAll();
        applicationRepository.deleteAll();
        userRepository.deleteAll();

        owner = userRepository.save(new User("owner@example.com", "hash"));
        JobApplication app = new JobApplication(owner, "Acme Corp", "Backend Engineer");
        app.setJobDescription(JOB);
        applicationId = applicationRepository.save(app).getId();
        resumeId = resumeRepository.save(new Resume(owner, "cv.pdf",
            "resumes/" + owner.getId() + "/" + UUID.randomUUID() + ".pdf", 1024L, RESUME_TEXT)).getId();

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @AfterEach
    void tearDown() {
        sessions.forEach(s -> { if (s.isConnected()) s.disconnect(); });
        stompClient.stop();
    }

    @Test
    void ownerIsNotifiedWhenAnalysisCompletes() throws Exception {
        BlockingQueue<AnalysisNotification> inbox = subscribe(connect(owner));

        Long analysisId = triggerAnalysis();

        AnalysisNotification notification = inbox.poll(10, TimeUnit.SECONDS);
        assertThat(notification).isNotNull();
        assertThat(notification.analysisId()).isEqualTo(analysisId);
        assertThat(notification.applicationId()).isEqualTo(applicationId);
        assertThat(notification.status()).isEqualTo("COMPLETED");
        assertThat(notification.matchScore()).isEqualTo(60);
        assertThat(notification.message()).isEqualTo("Analysis complete: 60% match");
    }

    @Test
    void ownerIsNotifiedWhenAnalysisFails() throws Exception {
        doThrow(new AiProviderException("AI provider timed out")).when(mockAiProvider).analyze(any());
        BlockingQueue<AnalysisNotification> inbox = subscribe(connect(owner));

        Long analysisId = triggerAnalysis();

        AnalysisNotification notification = inbox.poll(10, TimeUnit.SECONDS);
        assertThat(notification).isNotNull();
        assertThat(notification.analysisId()).isEqualTo(analysisId);
        assertThat(notification.status()).isEqualTo("FAILED");
        assertThat(notification.matchScore()).isNull();
        assertThat(notification.message()).isEqualTo("Analysis failed: AI provider timed out");
    }

    @Test
    void otherUsersDoNotReceiveTheNotification() throws Exception {
        User intruder = userRepository.save(new User("intruder@example.com", "hash"));
        BlockingQueue<AnalysisNotification> ownerInbox = subscribe(connect(owner));
        BlockingQueue<AnalysisNotification> intruderInbox = subscribe(connect(intruder));

        triggerAnalysis();

        assertThat(ownerInbox.poll(10, TimeUnit.SECONDS)).isNotNull();
        assertThat(intruderInbox.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void connectWithoutTokenIsRejected() throws Exception {
        assertConnectRejected(new StompHeaders(), "Missing bearer token");
    }

    @Test
    void connectWithInvalidTokenIsRejected() throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer not-a-real-token");
        assertConnectRejected(headers, "Invalid or expired token");
    }

    private void assertConnectRejected(StompHeaders headers, String expectedError) throws InterruptedException {
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        CompletableFuture<StompSession> future = connectAsync(headers, errorCollector(errors));

        assertThat(errors.poll(5, TimeUnit.SECONDS)).contains(expectedError);
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
    }

    @Test
    void subscribingToAnyOtherDestinationIsRejected() throws Exception {
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompSession session = connect(owner, errorCollector(errors));

        // A resolved per-session broker queue: the way to eavesdrop if raw destinations were allowed.
        session.subscribe("/queue/analyses-user" + UUID.randomUUID(), new IgnoringFrameHandler());

        assertThat(errors.poll(5, TimeUnit.SECONDS)).contains("Subscription not allowed");
    }

    @Test
    void clientsCannotSendMessages() throws Exception {
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompSession session = connect(owner, errorCollector(errors));

        session.send("/app/anything", "hello");

        assertThat(errors.poll(5, TimeUnit.SECONDS)).contains("Sending messages is not supported");
    }

    // ------------------------------------------------------------------ //

    /** Collects the message header of ERROR frames the server sends. */
    private static StompSessionHandlerAdapter errorCollector(BlockingQueue<String> errors) {
        return new StompSessionHandlerAdapter() {
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errors.add(String.valueOf(headers.getFirst("message")));
            }
        };
    }

    private StompSession connect(User user) throws Exception {
        return connect(user, new StompSessionHandlerAdapter() {});
    }

    private StompSession connect(User user, StompSessionHandlerAdapter handler) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", jwtHelper.authHeader(user));
        StompSession session = connectAsync(headers, handler).get(5, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private CompletableFuture<StompSession> connectAsync(StompHeaders headers, StompSessionHandlerAdapter handler) {
        return stompClient.connectAsync("ws://localhost:" + port + "/api/ws",
            new WebSocketHttpHeaders(), headers, handler);
    }

    private BlockingQueue<AnalysisNotification> subscribe(StompSession session) throws InterruptedException {
        BlockingQueue<AnalysisNotification> inbox = new LinkedBlockingQueue<>();
        session.subscribe(QUEUE, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return AnalysisNotification.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add((AnalysisNotification) payload);
            }
        });
        // The simple broker sends no receipts, so give the SUBSCRIBE frame time to register.
        Thread.sleep(300);
        return inbox;
    }

    private Long triggerAnalysis() throws Exception {
        String json = mockMvc.perform(post("/analyses")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", jwtHelper.authHeader(owner))
                .content("{\"applicationId\": " + applicationId + ", \"resumeId\": " + resumeId + "}"))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private static class IgnoringFrameHandler implements StompFrameHandler {
        @Override public Type getPayloadType(StompHeaders headers) { return Object.class; }
        @Override public void handleFrame(StompHeaders headers, Object payload) {}
    }
}
