package com.org.config;

import com.org.vectorstore.VectorStoreWriteProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A single shared, bounded executor for parallel vector-store writes. Replaces the previous
 * per-request {@code Executors.newFixedThreadPool(...)} (which churned threads and allowed
 * unbounded parallelism under concurrent ingestion). The bounded queue + caller-runs policy
 * applies natural back-pressure instead of piling up unbounded work.
 */
@Configuration
class VectorStoreWriteConfig {

    @Bean(destroyMethod = "shutdown")
    ExecutorService vectorStoreWriteExecutor(VectorStoreWriteProperties props) {
        int threads = Math.max(1, props.getConcurrency());
        AtomicInteger counter = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threads, threads,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(threads, props.getQueueCapacity())),
                r -> {
                    Thread t = new Thread(r, "vstore-write-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
