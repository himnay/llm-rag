package com.org.common;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal, dependency-free circuit breaker kept as a lightweight fallback for contexts where
 * Resilience4j is not available. The reranking post-processor uses Resilience4j instead.
 *
 * <p>States: CLOSED (normal) → OPEN (after threshold failures) → HALF-OPEN (one probe allowed
 * after cooldown) → CLOSED (on probe success) / OPEN (on probe failure).
 *
 * <p>Unlike the previous version, half-open allows <em>exactly one</em> probe per cooldown
 * period: a CAS on a HALF_OPEN sentinel prevents multiple concurrent probes from all reaching
 * the dependency simultaneously.
 */
public final class CircuitBreaker {

    private final int failureThreshold;
    private final long cooldownMillis;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private volatile long openedAtMillis;

    /**
     * Creates a circuit breaker that opens after {@code failureThreshold} consecutive failures
     * and allows one probe request after {@code cooldown} has elapsed.
     */
    public CircuitBreaker(int failureThreshold, Duration cooldown) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldownMillis = cooldown.toMillis();
    }

    /**
     * {@code true} when the call should be attempted.
     * At most one concurrent caller gets {@code true} in HALF_OPEN state.
     */
    public boolean allowRequest() {
        State s = state.get();
        if (s == State.CLOSED) {
            return true;
        }
        if (s == State.OPEN) {
            if (System.currentTimeMillis() - openedAtMillis >= cooldownMillis) {
                // Transition to HALF_OPEN; only one thread wins the CAS.
                return state.compareAndSet(State.OPEN, State.HALF_OPEN);
            }
            return false;
        }
        // HALF_OPEN: the probing thread is already in flight — reject all others.
        return false;
    }

    /**
     * Records a successful call: resets the failure count and closes the circuit.
     */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        state.set(State.CLOSED);
    }

    /**
     * Records a failed call, opening the circuit once {@code failureThreshold} consecutive
     * failures have been recorded.
     */
    public void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openedAtMillis = System.currentTimeMillis();
            state.set(State.OPEN);
        }
    }

    private enum State {CLOSED, OPEN, HALF_OPEN}
}
