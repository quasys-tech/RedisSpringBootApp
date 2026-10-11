package com.quasys.redis.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Enterprise Spring Cache Abstraction Configuration with Redis.
 *
 * Best practices demonstrated:
 * 1. Default TTL to prevent unbounded cache memory growth.
 * 2. Per-cache custom TTLs (e.g., short-lived vs long-lived domain caches).
 * 3. Graceful degradation via custom CacheErrorHandler: if Redis is temporarily unreachable,
 *    cache errors are logged instead of throwing exceptions, allowing business requests to succeed.
 * 4. Structured key prefixing and JSON value serialization.
 */
@Configuration
@EnableCaching
public class CacheConfiguration implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfiguration.class);

    private final RedisConfigurationProperties properties;

    public CacheConfiguration(RedisConfigurationProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        String keyPrefix = properties.getNormalizedKeyPrefix();
        log.info("Configuring Redis cache with global key prefix: '{}'", keyPrefix);

        // Default Cache Configuration: 10 minutes TTL, JSON serialization, no nulls cached, ACL prefix + clean folder hierarchy
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> keyPrefix + cacheName + ":")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        // Configure custom TTLs per cache name
        Map<String, RedisCacheConfiguration> initialCacheConfigurations = new HashMap<>();
        properties.getCacheTtls().forEach((cacheName, ttlSeconds) -> {
            log.info("Setting custom TTL for cache '{}': {} seconds", cacheName, ttlSeconds);
            initialCacheConfigurations.put(cacheName, defaultConfig.entryTtl(Duration.ofSeconds(ttlSeconds)));
        });

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(initialCacheConfigurations)
                .build();
    }

    /**
     * Best practice for High Availability: Graceful degradation CacheErrorHandler.
     * If Redis is temporarily unavailable or undergoes a failover, application operations
     * will fall back gracefully to the underlying database / service logic rather than failing.
     * Extracts and logs the root cause from Redis (e.g., NOPERM ACL violations, timeouts)
     * so developers can immediately diagnose issues.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                String rootCause = extractRootCauseMessage(exception);
                log.warn("Redis Cache GET error on cache '{}' for key '{}': {}. Root cause: '{}'. Falling back to source.",
                        cache.getName(), key, exception.getMessage(), rootCause);
                if (log.isDebugEnabled()) {
                    log.debug("Redis Cache GET exception details:", exception);
                }
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                String rootCause = extractRootCauseMessage(exception);
                log.warn("Redis Cache PUT error on cache '{}' for key '{}': {}. Root cause: '{}'. Continuing execution.",
                        cache.getName(), key, exception.getMessage(), rootCause);
                if (log.isDebugEnabled()) {
                    log.debug("Redis Cache PUT exception details:", exception);
                }
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                String rootCause = extractRootCauseMessage(exception);
                log.warn("Redis Cache EVICT error on cache '{}' for key '{}': {}. Root cause: '{}'. Continuing execution.",
                        cache.getName(), key, exception.getMessage(), rootCause);
                if (log.isDebugEnabled()) {
                    log.debug("Redis Cache EVICT exception details:", exception);
                }
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                String rootCause = extractRootCauseMessage(exception);
                log.warn("Redis Cache CLEAR error on cache '{}': {}. Root cause: '{}'. Continuing execution.",
                        cache.getName(), exception.getMessage(), rootCause);
                if (log.isDebugEnabled()) {
                    log.debug("Redis Cache CLEAR exception details:", exception);
                }
            }

            private String extractRootCauseMessage(Throwable throwable) {
                Throwable rootCause = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(throwable);
                return rootCause != null ? rootCause.getMessage() : throwable.getMessage();
            }
        };
    }
}
