package com.org.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticCacheServiceTest {

    @Mock
    EmbeddingCacheService embeddingCacheService;

    private SemanticCacheProperties props(boolean enabled, double threshold, int maxSize) {
        SemanticCacheProperties p = new SemanticCacheProperties();
        p.setEnabled(enabled);
        p.setSimilarityThreshold(threshold);
        p.setMaxSize(maxSize);
        p.setTtl(Duration.ofMinutes(30));
        return p;
    }

    /** Returns a unit vector pointing in the direction (x, y). */
    private static float[] unit(float x, float y) {
        float norm = (float) Math.sqrt(x * x + y * y);
        return new float[]{x / norm, y / norm};
    }

    @Test
    void whenDisabledGetAlwaysReturnsEmpty() {
        SemanticCacheService cache = new SemanticCacheService(embeddingCacheService, props(false, 0.9, 10));
        cache.put("hello", "world");
        assertThat(cache.get("hello")).isEmpty();
    }

    @Test
    void identicalQueryHitsCache() {
        float[] v = unit(1f, 0f);
        when(embeddingCacheService.embed("hello")).thenReturn(v);

        SemanticCacheService cache = new SemanticCacheService(embeddingCacheService, props(true, 0.9, 10));
        cache.put("hello", "the answer");

        assertThat(cache.get("hello")).contains("the answer");
    }

    @Test
    void orthogonalQueryMissesCache() {
        when(embeddingCacheService.embed("question")).thenReturn(unit(1f, 0f));
        when(embeddingCacheService.embed("unrelated")).thenReturn(unit(0f, 1f));

        SemanticCacheService cache = new SemanticCacheService(embeddingCacheService, props(true, 0.9, 10));
        cache.put("question", "answer");

        // cosine similarity of [1,0] and [0,1] = 0.0, well below threshold 0.9
        assertThat(cache.get("unrelated")).isEmpty();
    }

    @Test
    void putWhenDisabledDoesNothing() {
        SemanticCacheService cache = new SemanticCacheService(embeddingCacheService, props(false, 0.9, 10));
        // Should not throw or call embeddingCacheService
        cache.put("query", "answer");
    }

    @Test
    void evictsOldestEntryWhenFull() {
        // q1 → distinct vector; q2, q3 → shared vector so they are "similar" to each other
        when(embeddingCacheService.embed("q1")).thenReturn(unit(1f, 0f));
        when(embeddingCacheService.embed("q2")).thenReturn(unit(0f, 1f));
        when(embeddingCacheService.embed("q3")).thenReturn(unit(0f, 1f));

        SemanticCacheService cache = new SemanticCacheService(embeddingCacheService, props(true, 0.9, 2));
        cache.put("q1", "a1");
        cache.put("q2", "a2");
        cache.put("q3", "a3"); // evicts q1

        // q1 vector is orthogonal to remaining entries → no hit
        assertThat(cache.get("q1")).isEmpty();
        // q2 / q3 share the same vector direction → cache hit on remaining entries
        assertThat(cache.get("q2")).isPresent();
    }

    @Test
    void belowThresholdQueryMisses() {
        // Both queries in the same quadrant but slightly off — use a strict threshold
        when(embeddingCacheService.embed("original")).thenReturn(unit(1f, 0f));
        when(embeddingCacheService.embed("similar")).thenReturn(unit(1f, 0.5f));

        // threshold = 1.0 means only exact vector match succeeds
        SemanticCacheService cache = new SemanticCacheService(embeddingCacheService, props(true, 1.0, 10));
        cache.put("original", "answer");

        // unit(1,0) · unit(1,0.5) ≈ 0.894 < 1.0 → miss
        assertThat(cache.get("similar")).isEmpty();
    }
}
