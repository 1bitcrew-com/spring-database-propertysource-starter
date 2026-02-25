package com.example.dbconfig.core;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;

public final class RetryExecutor {

    private RetryExecutor() {
    }

    public static <T> T execute(Supplier<T> action, RetryPolicy policy, Logger log, String operationName) {
        RuntimeException lastRuntimeException = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                return action.get();
            }
            catch (RuntimeException ex) {
                lastRuntimeException = ex;
                if (attempt >= policy.maxAttempts()) {
                    throw ex;
                }
                Duration delay = computeDelay(policy, attempt);
                log.debug("Operation '{}' failed at attempt {}/{}. Retrying in {} ms.",
                        operationName,
                        attempt,
                        policy.maxAttempts(),
                        delay.toMillis(),
                        ex);
                sleep(delay);
            }
        }
        throw lastRuntimeException;
    }

    private static Duration computeDelay(RetryPolicy policy, int attempt) {
        long initialMs = Math.max(0L, policy.initialBackoff().toMillis());
        long maxMs = Math.max(initialMs, policy.maxBackoff().toMillis());

        long expBase;
        if (attempt <= 1) {
            expBase = initialMs;
        }
        else {
            long factor = 1L << Math.min(30, attempt - 1);
            expBase = Math.min(maxMs, initialMs * factor);
        }

        double jitter = Math.max(0.0d, Math.min(1.0d, policy.jitter()));
        double jitterFactor = (1 - jitter) + ThreadLocalRandom.current().nextDouble(0.0d, 2 * jitter);
        long withJitter = Math.max(0L, Math.min(maxMs, Math.round(expBase * jitterFactor)));
        return Duration.ofMillis(withJitter);
    }

    private static void sleep(Duration delay) {
        if (delay.isZero() || delay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", ex);
        }
    }

    public record RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff, double jitter) {
    }
}
