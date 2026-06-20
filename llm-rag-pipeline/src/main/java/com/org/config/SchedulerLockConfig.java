package com.org.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Backs {@code @SchedulerLock} (ShedLock) with Redis so {@code @Scheduled} tasks — currently just
 * {@code InboxScheduler.scan()} — run on only one instance at a time when this app is scaled
 * horizontally. Without this, every instance would poll and ingest from the same inbox folder
 * concurrently, double-processing files.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory redisConnectionFactory) {
        return new RedisLockProvider(redisConnectionFactory, "llm-rag-pipeline");
    }
}
