package com.quasys.redis.config;

import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Custom StringRedisSerializer that transparently adds a configurable namespace prefix to all Redis keys.
 * 
 * Purpose:
 * Enables Redis Enterprise Multi-Tenancy with Redis 6+ ACL (Access Control Lists).
 * Multiple teams or microservices can share the same Redis database securely when restricted
 * to specific key patterns (e.g., ~quasys:* or ~team_alpha:*).
 */
public class PrefixStringRedisSerializer extends StringRedisSerializer {

    private final String prefix;

    public PrefixStringRedisSerializer(String prefix) {
        this(prefix, StandardCharsets.UTF_8);
    }

    public PrefixStringRedisSerializer(String prefix, Charset charset) {
        super(charset);
        this.prefix = prefix != null ? prefix : "";
    }

    @Override
    public byte[] serialize(@Nullable String string) {
        if (string == null) {
            return null;
        }
        String keyWithPrefix = StringUtils.hasText(prefix) && !string.startsWith(prefix)
                ? prefix + string
                : string;
        return super.serialize(keyWithPrefix);
    }

    @Override
    public String deserialize(@Nullable byte[] bytes) {
        String string = super.deserialize(bytes);
        if (string != null && StringUtils.hasText(prefix) && string.startsWith(prefix)) {
            return string.substring(prefix.length());
        }
        return string;
    }

    public String getPrefix() {
        return prefix;
    }
}
