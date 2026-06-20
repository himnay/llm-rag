package com.org.config;

import com.knuddels.jtokkit.api.EncodingType;
import com.org.vectorstore.VectorStoreWriteProperties;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
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

    /**
     * Builds the bounded, caller-runs-backpressured executor shared by all vector-store writes.
     */
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

    /**
     * Splits each {@code vectorStore.add(batch)} call further by token count, so a batch of
     * {@code app.vectorstore.write.batch-size} chunks never exceeds the embedding vendor's
     * per-request token ceiling ({@code max-tokens-per-batch}) regardless of how many chunks that
     * is — independent of (and beneath) the chunk-count batching {@code ChunkVectorStoreService}
     * already does.
     */
    @Bean
    BatchingStrategy batchingStrategy(VectorStoreWriteProperties props) {
        return new TokenCountBatchingStrategy(EncodingType.CL100K_BASE,
                props.getMaxTokensPerBatch(), props.getTokenReservePercentage());
    }
}
