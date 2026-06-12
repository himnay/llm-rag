package com.org.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerTest {

    @Test
    void staysClosedBelowTheFailureThreshold() {
        CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofMinutes(1));
        breaker.recordFailure();
        breaker.recordFailure();
        assertThat(breaker.allowRequest()).isTrue();
    }

    @Test
    void opensAfterConsecutiveFailures() {
        CircuitBreaker breaker = new CircuitBreaker(2, Duration.ofMinutes(1));
        breaker.recordFailure();
        breaker.recordFailure();
        assertThat(breaker.allowRequest()).isFalse();
    }

    @Test
    void successResetsTheFailureStreak() {
        CircuitBreaker breaker = new CircuitBreaker(2, Duration.ofMinutes(1));
        breaker.recordFailure();
        breaker.recordSuccess();
        breaker.recordFailure();
        assertThat(breaker.allowRequest()).isTrue();
    }

    @Test
    void allowsAHalfOpenProbeAfterTheCooldown() {
        CircuitBreaker breaker = new CircuitBreaker(1, Duration.ZERO);
        breaker.recordFailure();
        assertThat(breaker.allowRequest()).isTrue();  // cooldown of 0 → probe immediately
        breaker.recordSuccess();
        assertThat(breaker.allowRequest()).isTrue();  // probe success closes the breaker
    }
}
