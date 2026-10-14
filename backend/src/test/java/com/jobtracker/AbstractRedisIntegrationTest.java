package com.jobtracker;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

/**
 * Integration tests that need a real Redis for the cache. Same singleton-container
 * approach as {@link AbstractIntegrationTest}, and switches the cache from the test
 * profile's {@code none} to {@code redis}.
 */
public abstract class AbstractRedisIntegrationTest extends AbstractIntegrationTest {

    public static final String REDIS_IMAGE = "redis:8.10.2-alpine";

    static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.cache.type", () -> "redis");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
