package com.jobtracker.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dashboard.api.DashboardStatsResponse;
import com.jobtracker.dashboard.domain.DashboardService;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Clock;
import java.time.Duration;

/**
 * Caching through Spring's cache abstraction; the store is chosen with
 * {@code spring.cache.type} ({@code redis} by default, {@code none} to disable).
 *
 * <ul>
 *   <li>Keys: {@code <prefix><cache name>:<key>}, e.g. {@code jobtracker:dashboard:stats:42}.
 *       The prefix namespaces this application in a Redis shared with other projects.</li>
 *   <li>Values: JSON written with a serializer bound to the cached type, so no class
 *       names are stored and nothing from Redis can choose which class is created.</li>
 *   <li>Failures: cache errors are logged and ignored. A failed read behaves like a
 *       miss, so requests fall back to PostgreSQL when Redis is unavailable.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheCustomizer(
            ObjectMapper objectMapper,
            @Value("${app.cache.key-prefix:jobtracker:}") String keyPrefix,
            @Value("${app.cache.dashboard-ttl:5m}") Duration dashboardTtl) {
        RedisCacheConfiguration dashboardStats = RedisCacheConfiguration.defaultCacheConfig()
            .computePrefixWith(cacheName -> keyPrefix + cacheName + ":")
            .entryTtl(dashboardTtl)
            .disableCachingNullValues()
            .serializeValuesWith(SerializationPair.fromSerializer(
                new Jackson2JsonRedisSerializer<>(objectMapper, DashboardStatsResponse.class)));
        return builder -> builder.withCacheConfiguration(DashboardService.STATS_CACHE, dashboardStats);
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(LogFactory.getLog(CacheConfig.class), false);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
