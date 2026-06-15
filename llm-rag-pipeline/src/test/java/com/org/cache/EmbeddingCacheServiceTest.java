package com.org.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmbeddingCacheServiceTest {

    @Mock
    EmbeddingModel embeddingModel;

    private EmbeddingCacheProperties enabled() {
        EmbeddingCacheProperties p = new EmbeddingCacheProperties();
        p.setEnabled(true);
        p.setMaxSize(10);
        p.setTtl(Duration.ofHours(1));
        return p;
    }

    private EmbeddingCacheProperties disabled() {
        EmbeddingCacheProperties p = new EmbeddingCacheProperties();
        p.setEnabled(false);
        p.setMaxSize(10);
        p.setTtl(Duration.ofHours(1));
        return p;
    }

    @Test
    void whenDisabledAlwaysDelegatesToModel() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f, 0f});
        EmbeddingCacheService service = new EmbeddingCacheService(embeddingModel, disabled());

        service.embed("hello");
        service.embed("hello");

        verify(embeddingModel, times(2)).embed("hello");
    }

    @Test
    void cacheHitAvoidsDelegation() {
        when(embeddingModel.embed("hello")).thenReturn(new float[]{0.1f, 0.9f});
        EmbeddingCacheService service = new EmbeddingCacheService(embeddingModel, enabled());

        float[] first = service.embed("hello");
        float[] second = service.embed("hello");

        verify(embeddingModel, times(1)).embed("hello");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void cacheMissCallsModelAndStoresResult() {
        when(embeddingModel.embed("foo")).thenReturn(new float[]{1f});
        when(embeddingModel.embed("bar")).thenReturn(new float[]{2f});
        EmbeddingCacheService service = new EmbeddingCacheService(embeddingModel, enabled());

        assertThat(service.embed("foo")).containsExactly(1f);
        assertThat(service.embed("bar")).containsExactly(2f);
        // Second calls must hit cache, not model
        service.embed("foo");
        service.embed("bar");

        verify(embeddingModel, times(1)).embed("foo");
        verify(embeddingModel, times(1)).embed("bar");
    }

    @Test
    void batchEmbedOnlyFetchesUncachedTexts() {
        when(embeddingModel.embed("foo")).thenReturn(new float[]{1f});
        when(embeddingModel.embed(List.of("bar"))).thenReturn(List.of(new float[]{2f}));
        EmbeddingCacheService service = new EmbeddingCacheService(embeddingModel, enabled());

        service.embed("foo"); // prime cache for "foo"
        List<float[]> result = service.embed(List.of("foo", "bar"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsExactly(1f); // from cache
        assertThat(result.get(1)).containsExactly(2f); // from model
        // "foo" must not appear in any batch call
        verify(embeddingModel, never()).embed(List.of("foo", "bar"));
    }

    @Test
    void batchEmbedWhenDisabledDelegatesToModel() {
        List<float[]> vectors = List.of(new float[]{1f}, new float[]{2f});
        when(embeddingModel.embed(List.of("a", "b"))).thenReturn(vectors);
        EmbeddingCacheService service = new EmbeddingCacheService(embeddingModel, disabled());

        assertThat(service.embed(List.of("a", "b"))).isEqualTo(vectors);
        verify(embeddingModel, times(1)).embed(List.of("a", "b"));
    }
}
