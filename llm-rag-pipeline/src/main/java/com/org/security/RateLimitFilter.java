package com.org.security;

import com.org.web.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight, dependency-free token-bucket rate limiter for {@code /api/**}. Each caller (keyed by
 * API key when present, otherwise client IP) gets a bucket that refills continuously; exhausting it
 * yields {@code 429}. In-memory — for multi-instance deployments back this with Redis/a gateway.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String PREFIX = "/api/";
    private static final ThreadLocal<MessageDigest> SHA256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    });
    private final SecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static String sha256(String value) {
        MessageDigest md = SHA256.get();
        md.reset();
        return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.getRateLimit().isEnabled() || !request.getRequestURI().startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = clientKey(request);
        if (bucketFor(key).tryConsume()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                ApiError.of(HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests",
                        "Rate limit exceeded — slow down and retry"));
    }

    private String clientKey(HttpServletRequest request) {
        String apiKey = request.getHeader(properties.getHeader());
        if (apiKey != null && !apiKey.isBlank()) {
            // SHA-256 avoids the 2^32 hashCode collision space that allowed bucket-sharing attacks.
            return "key:" + sha256(apiKey);
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        return "ip:" + (forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim() : request.getRemoteAddr());
    }

    private Bucket bucketFor(String key) {
        SecurityProperties.RateLimit cfg = properties.getRateLimit();
        return buckets.computeIfAbsent(key, k ->
                new Bucket(cfg.getCapacity(), cfg.getRefillPerMinute() / 60.0));
    }

    /**
     * Simple continuous-refill token bucket.
     */
    private static final class Bucket {
        private final double capacity;
        private final double refillPerSecond;
        private double tokens;
        private long lastRefillNanos;

        Bucket(double capacity, double refillPerSecond) {
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
            lastRefillNanos = now;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
