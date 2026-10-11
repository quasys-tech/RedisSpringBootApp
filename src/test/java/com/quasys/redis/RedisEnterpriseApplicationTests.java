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

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @Test
    void testCacheKeyPrefix() {
        org.springframework.data.redis.cache.RedisCache cache = (org.springframework.data.redis.cache.RedisCache) cacheManager.getCache("items");
        org.junit.jupiter.api.Assertions.assertNotNull(cache);
        org.springframework.data.redis.cache.RedisCacheConfiguration config = cache.getCacheConfiguration();
        org.junit.jupiter.api.Assertions.assertEquals("quasys:items:", config.getKeyPrefixFor("items"));
    }

    @Test
    void testRedisTemplateKeyPrefix() {
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.serializer.RedisSerializer<String> keySerializer = 
                (org.springframework.data.redis.serializer.RedisSerializer<String>) redisTemplate.getKeySerializer();
        org.junit.jupiter.api.Assertions.assertNotNull(keySerializer);

        // Serialization should add prefix
        byte[] serialized = keySerializer.serialize("myKey");
        String inRedis = new String(serialized, java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertEquals("quasys:myKey", inRedis);

        // Idempotency: if string already starts with prefix, do not duplicate
        byte[] alreadyPrefixed = keySerializer.serialize("quasys:myKey");
        org.junit.jupiter.api.Assertions.assertEquals("quasys:myKey", new String(alreadyPrefixed, java.nio.charset.StandardCharsets.UTF_8));

        // Deserialization should transparently strip prefix
        String deserialized = keySerializer.deserialize(serialized);
        org.junit.jupiter.api.Assertions.assertEquals("myKey", deserialized);
    }
}
