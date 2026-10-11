package com.quasys.redis.controller;

import com.quasys.redis.config.RedisConfigurationProperties;
import com.quasys.redis.model.Item;
import com.quasys.redis.service.ItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller exposing endpoints to test and verify Redis Enterprise connectivity and caching.
 */
@RestController
@RequestMapping("/api")
public class RedisDemoController {

    private static final Logger log = LoggerFactory.getLogger(RedisDemoController.class);

    private final ItemService itemService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisConfigurationProperties properties;
    private final RedisProperties redisProperties;

    public RedisDemoController(ItemService itemService,
                               RedisTemplate<String, Object> redisTemplate,
                               RedisConfigurationProperties properties,
                               RedisProperties redisProperties) {
        this.itemService = itemService;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.redisProperties = redisProperties;
    }

    /**
     * Checks Redis connectivity by executing a PING command.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> response = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        try {
            String pingResult = redisTemplate.execute((RedisConnection connection) -> connection.ping());
            long elapsed = System.currentTimeMillis() - start;

            response.put("status", "UP");
            response.put("redisPing", pingResult);
            response.put("latencyMs", elapsed);
            response.put("timestamp", LocalDateTime.now());
            response.put("message", "Connected successfully to Redis Enterprise!");
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - start;
            Throwable rootCause = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(ex);
            String rootCauseMessage = rootCause != null ? rootCause.getMessage() : ex.getMessage();
            log.error("Failed to ping Redis: {}. Root cause: {}", ex.getMessage(), rootCauseMessage);
            response.put("status", "DOWN");
            response.put("latencyMs", elapsed);
            response.put("timestamp", LocalDateTime.now());
            response.put("error", ex.getMessage());
            response.put("rootCause", rootCauseMessage);
            response.put("message", "Could not connect to Redis. Please verify host, port, credentials, and SSL settings.");
            return ResponseEntity.status(503).body(response);
        }
    }

    /**
     * Returns the active configuration summary.
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfo() {
        Map<String, Object> info = new LinkedHashMap<>();

        // 1. Connection overview
        Map<String, Object> connectionInfo = new LinkedHashMap<>();
        connectionInfo.put("host", redisProperties.getHost());
        connectionInfo.put("port", redisProperties.getPort());
        connectionInfo.put("username", redisProperties.getUsername());
        connectionInfo.put("databaseIndex", redisProperties.getDatabase());
        connectionInfo.put("keyPrefix", properties.getNormalizedKeyPrefix());
        connectionInfo.put("timeout", redisProperties.getTimeout());
        info.put("connection", connectionInfo);

        // 2. Client & Pooling status
        Map<String, Object> clientInfo = new LinkedHashMap<>();
        clientInfo.put("driver", "Lettuce (Netty-based reactive driver)");

        org.springframework.data.redis.connection.RedisConnectionFactory factory = redisTemplate.getConnectionFactory();
        if (factory instanceof org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory lettuceFactory) {
            boolean isPooled = lettuceFactory.getClientConfiguration() instanceof org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
            clientInfo.put("poolingEnabled", isPooled);
            clientInfo.put("shareNativeConnection", lettuceFactory.getShareNativeConnection());
            if (isPooled && redisProperties.getLettuce().getPool() != null) {
                Map<String, Object> poolStats = new LinkedHashMap<>();
                poolStats.put("maxActive", redisProperties.getLettuce().getPool().getMaxActive());
                poolStats.put("maxIdle", redisProperties.getLettuce().getPool().getMaxIdle());
                poolStats.put("minIdle", redisProperties.getLettuce().getPool().getMinIdle());
                poolStats.put("maxWait", redisProperties.getLettuce().getPool().getMaxWait());
                clientInfo.put("poolConfiguration", poolStats);
            }
        }
        clientInfo.put("clusterMode", properties.getCluster().isEnabled());
        clientInfo.put("protocol", properties.getProtocol());
        clientInfo.put("autoReconnect", properties.isAutoReconnect());
        clientInfo.put("pingBeforeActivate", properties.isPingBeforeActivate());
        clientInfo.put("reconnectDelayMax", properties.getReconnectDelayMax());
        info.put("client", clientInfo);

        // 3. Socket / TCP Tuning
        Map<String, Object> socketInfo = new LinkedHashMap<>();
        socketInfo.put("keepAlive", properties.getSocket().isKeepAlive());
        socketInfo.put("tcpNoDelay", properties.getSocket().isTcpNoDelay());
        socketInfo.put("connectTimeout", properties.getSocket().getConnectTimeout());
        info.put("socketTuning", socketInfo);

        // 4. SSL & SNI details
        Map<String, Object> sslDetails = new LinkedHashMap<>();
        sslDetails.put("enabled", properties.getSsl().isEnabled());
        sslDetails.put("useSni", properties.getSsl().isUseSni());
        sslDetails.put("sniServerName", properties.getSsl().getSniServerName());
        sslDetails.put("disablePeerVerification", properties.getSsl().isDisablePeerVerification());
        info.put("sslTls", sslDetails);

        // 5. Cache TTL configurations
        info.put("cacheTtlsSeconds", properties.getCacheTtls());

        return ResponseEntity.ok(info);
    }

    /**
     * Demonstrates @Cacheable. First call takes ~1s (DB latency), subsequent calls are sub-millisecond.
     */
    @GetMapping("/items/{id}")
    public ResponseEntity<Item> getItem(@PathVariable String id) {
        long start = System.currentTimeMillis();
        Item item = itemService.getItemById(id);
        long duration = System.currentTimeMillis() - start;
        log.info("getItem({}) took {} ms", id, duration);

        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(item);
    }

    /**
     * Demonstrates @CachePut. Updates item and updates cache.
     */
    @PutMapping("/items/{id}")
    public ResponseEntity<Item> updateItem(@PathVariable String id, @RequestBody Item item) {
        item.setId(id);
        if (item.getCreatedAt() == null) {
            item.setCreatedAt(LocalDateTime.now());
        }
        Item updated = itemService.updateItem(item);
        return ResponseEntity.ok(updated);
    }

    /**
     * Demonstrates @CacheEvict. Deletes item from DB and clears cache entry.
     */
    @DeleteMapping("/items/{id}")
    public ResponseEntity<Map<String, String>> deleteItem(@PathVariable String id) {
        itemService.deleteItem(id);
        return ResponseEntity.ok(Map.of("message", "Item " + id + " deleted and evicted from cache."));
    }

    /**
     * Demonstrates high-performance pipelined batch operations.
     */
    @PostMapping("/items/pipeline-demo")
    public ResponseEntity<Map<String, Object>> runPipelineDemo(@RequestParam(defaultValue = "10") int count) {
        List<Item> batch = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            batch.add(new Item("pipe-" + i, "Batch Item " + i, "Batch", 10.0 * i, LocalDateTime.now()));
        }

        long start = System.currentTimeMillis();
        List<Object> results = itemService.batchInsertWithPipeline(batch);
        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("itemsProcessed", count);
        response.put("elapsedMs", elapsed);
        response.put("pipelineResultsCount", results.size());
        response.put("message", "Successfully executed pipelined batch write to Redis.");
        return ResponseEntity.ok(response);
    }

    /**
     * Demonstrates concurrent load testing on the Redis connection pool.
     * Spawns parallel threads executing simultaneous Redis commands.
     */
    @PostMapping("/pool/test-concurrency")
    public ResponseEntity<Map<String, Object>> testPoolConcurrency(
            @RequestParam(defaultValue = "10") int threads,
            @RequestParam(defaultValue = "50") int totalRequests) {

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger errorCount = new java.util.concurrent.atomic.AtomicInteger(0);

        long start = System.currentTimeMillis();
        List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 1; i <= totalRequests; i++) {
            final int id = i;
            java.util.concurrent.CompletableFuture<Void> future = java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    redisTemplate.opsForValue().set("pool:test:" + id, "concurrency-val-" + id);
                    redisTemplate.opsForValue().get("pool:test:" + id);
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    log.error("Concurrent pool operation error on task {}: {}", id, ex.getMessage());
                    errorCount.incrementAndGet();
                }
            }, executor);
            futures.add(future);
        }

        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
        long elapsed = System.currentTimeMillis() - start;
        executor.shutdown();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("threads", threads);
        response.put("totalRequests", totalRequests);
        response.put("successCount", successCount.get());
        response.put("errorCount", errorCount.get());
        response.put("totalElapsedMs", elapsed);
        response.put("avgLatencyPerRequestMs", (double) elapsed / totalRequests);
        response.put("message", "Concurrent pool load test completed successfully.");
        return ResponseEntity.ok(response);
    }
}
