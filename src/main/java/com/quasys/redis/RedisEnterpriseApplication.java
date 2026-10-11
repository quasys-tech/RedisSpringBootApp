package com.quasys.redis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot Application demonstrating Redis Enterprise Best Practices using Lettuce.
 */
@SpringBootApplication
public class RedisEnterpriseApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisEnterpriseApplication.class, args);
    }
}
