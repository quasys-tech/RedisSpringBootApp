package com.quasys.redis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RedisEnterpriseApplicationTests {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.boot.autoconfigure.data.redis.RedisProperties redisProperties;

    @Test
    void contextLoads() {
        org.junit.jupiter.api.Assertions.assertNotNull(connectionFactory);
        if (connectionFactory instanceof org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory lettuceFactory) {
            boolean isPooled = lettuceFactory.getClientConfiguration() instanceof org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
            org.junit.jupiter.api.Assertions.assertTrue(isPooled, "Lettuce connection pooling should be enabled");
            org.junit.jupiter.api.Assertions.assertNotNull(redisProperties.getLettuce().getPool());
            org.junit.jupiter.api.Assertions.assertEquals(16, redisProperties.getLettuce().getPool().getMaxActive());
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.data.redis.cache.RedisCacheManager cacheManager;

    @Test
    void testCacheKeyPrefix() {
        org.springframework.data.redis.cache.RedisCache cache = (org.springframework.data.redis.cache.RedisCache) cacheManager.getCache("items");
        org.junit.jupiter.api.Assertions.assertNotNull(cache);
        org.springframework.data.redis.cache.RedisCacheConfiguration config = cache.getCacheConfiguration();
        org.junit.jupiter.api.Assertions.assertEquals("items:", config.getKeyPrefixFor("items"));
    }
}
