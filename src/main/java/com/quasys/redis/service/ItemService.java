package com.quasys.redis.service;

import com.quasys.redis.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service demonstrating Redis Enterprise best practices:
 * 1. Spring Cache Abstraction (@Cacheable, @CachePut, @CacheEvict)
 * 2. High-performance batch pipelining via RedisTemplate.executePipelined()
 * 3. Fallback database emulation
 */
@Service
@CacheConfig(cacheNames = "items")
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);

    // Emulated persistent database
    private final Map<String, Item> database = new ConcurrentHashMap<>();

    private final RedisTemplate<String, Object> redisTemplate;

    public ItemService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        initSampleData();
    }

    private void initSampleData() {
        Item item1 = new Item("1", "Enterprise SSD", "Hardware", 299.99, LocalDateTime.now());
        Item item2 = new Item("2", "Cloud Database License", "Software", 1499.00, LocalDateTime.now());
        database.put(item1.getId(), item1);
        database.put(item2.getId(), item2);
    }

    /**
     * Cache-aside pattern using @Cacheable.
     * The first call hits the "database" (simulated with 1-second delay).
     * Subsequent calls return immediately from Redis cache until TTL expires or evicted.
     */
    @Cacheable(key = "#id")
    public Item getItemById(String id) {
        log.info("[DB ACCESS] Fetching item {} from database...", id);
        simulateSlowDatabaseCall();
        Item item = database.get(id);
        if (item == null) {
            log.info("Item {} not found in database.", id);
            return null;
        }
        return item;
    }

    /**
     * Updates item in database and updates Redis cache entry with @CachePut.
     */
    @CachePut(key = "#item.id")
    public Item updateItem(Item item) {
        log.info("[DB UPDATE] Updating item {} in database and updating cache.", item.getId());
        database.put(item.getId(), item);
        return item;
    }

    /**
     * Deletes item from database and invalidates the cached entry with @CacheEvict.
     */
    @CacheEvict(key = "#id")
    public void deleteItem(String id) {
        log.info("[DB DELETE] Deleting item {} from database and evicting from cache.", id);
        database.remove(id);
    }

    /**
     * Clears entire items cache namespace.
     */
    @CacheEvict(allEntries = true)
    public void clearAllCache() {
        log.info("[CACHE CLEAR] Evicting all items from cache namespace.");
    }

    /**
     * Best Practice: High-Performance Pipelining.
     * Bundles multiple Redis commands into a single round-trip using executePipelined.
     */
    public List<Object> batchInsertWithPipeline(List<Item> items) {
        log.info("Executing batch insert of {} items using Redis pipelining...", items.size());
        return redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                for (Item item : items) {
                    byte[] rawKey = redisTemplate.getStringSerializer().serialize("pipelined:item:" + item.getId());
                    @SuppressWarnings("unchecked")
                    byte[] rawValue = ((org.springframework.data.redis.serializer.RedisSerializer<Object>) 
                            redisTemplate.getValueSerializer()).serialize(item);
                    
                    if (rawKey != null && rawValue != null) {
                        connection.stringCommands().set(rawKey, rawValue);
                    }
                }
                return null; // executePipelined handles return values
            }
        });
    }

    private void simulateSlowDatabaseCall() {
        try {
            Thread.sleep(1000); // 1-second simulated DB latency
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
