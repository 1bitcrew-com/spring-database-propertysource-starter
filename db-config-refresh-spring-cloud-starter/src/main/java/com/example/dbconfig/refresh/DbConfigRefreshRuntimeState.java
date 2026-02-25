package com.example.dbconfig.refresh;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DbConfigRefreshRuntimeState {

    private final AtomicLong refreshTriggeredCount = new AtomicLong();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicInteger degraded = new AtomicInteger();
    private final AtomicLong lastSuccessEpochMillis = new AtomicLong(0L);

    public void onRefreshTriggered() {
        refreshTriggeredCount.incrementAndGet();
    }

    public long getRefreshTriggeredCount() {
        return refreshTriggeredCount.get();
    }

    public int incrementFailures() {
        return consecutiveFailures.incrementAndGet();
    }

    public void onSuccess() {
        consecutiveFailures.set(0);
        degraded.set(0);
        lastSuccessEpochMillis.set(Instant.now().toEpochMilli());
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public void setDegraded(boolean degradedMode) {
        degraded.set(degradedMode ? 1 : 0);
    }

    public int isDegraded() {
        return degraded.get();
    }

    public long getLastSuccessEpochMillis() {
        return lastSuccessEpochMillis.get();
    }
}
