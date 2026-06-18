package com.org.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerTest {

    @Test
    @DisplayName("Stays closed and allows requests below the failure threshold")
    void staysClosedBelowTheFailureThreshold() {
        CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofMinutes(1));
        breaker.recordFailure();
        breaker.recordFailure();
        assertThat(breaker.allowRequest()).isTrue();
    }

    @Test
    @DisplayName("Opens and blocks requests after consecutive failures reach the threshold")
    void opensAfterConsecutiveFailures() {
        CircuitBreaker breaker = new CircuitBreaker(2, Duration.ofMinutes(1));
        breaker.recordFailure();
        breaker.recordFailure();
        assertThat(breaker.allowRequest()).isFalse();
    }

    @Test
    @DisplayName("Resets the failure streak after a recorded success")
    void successResetsTheFailureStreak() {
        CircuitBreaker breaker = new CircuitBreaker(2, Duration.ofMinutes(1));
        breaker.recordFailure();
        breaker.recordSuccess();
        breaker.recordFailure();
        assertThat(breaker.allowRequest()).isTrue();
    }

    @Test
    @DisplayName("Allows a half-open probe after the cooldown and closes again on success")
    void allowsAHalfOpenProbeAfterTheCooldown() {
        CircuitBreaker breaker = new CircuitBreaker(1, Duration.ZERO);
        breaker.recordFailure();
        assertThat(breaker.allowRequest()).isTrue();  // cooldown of 0 → probe immediately
        breaker.recordSuccess();
        assertThat(breaker.allowRequest()).isTrue();  // probe success closes the breaker
    }
}
