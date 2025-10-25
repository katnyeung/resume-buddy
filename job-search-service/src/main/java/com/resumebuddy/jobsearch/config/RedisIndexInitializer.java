package com.resumebuddy.jobsearch.config;

import com.resumebuddy.jobsearch.service.RedisVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Initialize Redis vector index on startup
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RedisIndexInitializer {

    private final RedisVectorService redisVectorService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeRedisIndex() {
        try {
            log.info("Initializing Redis vector index...");
            redisVectorService.createVectorIndex();
            log.info("Redis vector index initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Redis vector index", e);
        }
    }
}
