package com.example.dbconfig.refresh;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class DbConfigRefreshState {

    private final AtomicLong refreshTriggeredCount = new AtomicLong();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicInteger degraded = new AtomicInteger();
    private final AtomicReference<Instant> lastDbVersionSeen = new AtomicReference<>(Instant.EPOCH);
    private final AtomicReference<Instant> lastSuccessRefresh = new AtomicReference<>(Instant.EPOCH);
    private final AtomicReference<Instant> lastAttemptRefresh = new AtomicReference<>(Instant.EPOCH);
    private final AtomicReference<String> lastErrorSummary = new AtomicReference<>();
    private final AtomicInteger lastSnapshotSize = new AtomicInteger();
    private final AtomicReference<List<String>> lastActiveProfiles = new AtomicReference<>(List.of());
    private final AtomicInteger initialLoadFailed = new AtomicInteger();

    public void onRefreshTriggered() {
        refreshTriggeredCount.incrementAndGet();
    }

    public long getRefreshTriggeredCount() {
        return refreshTriggeredCount.get();
    }

    public void onAttempt(Instant attemptAt, List<String> activeProfiles) {
        lastAttemptRefresh.set(attemptAt);
        lastActiveProfiles.set(List.copyOf(activeProfiles));
    }

    public void onSuccess(Instant now, Instant dbVersion, int snapshotSize) {
        consecutiveFailures.set(0);
        degraded.set(0);
        initialLoadFailed.set(0);
        lastSuccessRefresh.set(now);
        lastDbVersionSeen.set(dbVersion);
        lastSnapshotSize.set(snapshotSize);
        lastErrorSummary.set(null);
    }

    public int incrementFailures() {
        return consecutiveFailures.incrementAndGet();
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
        return lastSuccessRefresh.get().toEpochMilli();
    }

    public Instant getLastDbVersionSeen() {
        return lastDbVersionSeen.get();
    }

    public Instant getLastSuccessRefresh() {
        return lastSuccessRefresh.get();
    }

    public Instant getLastAttemptRefresh() {
        return lastAttemptRefresh.get();
    }

    public String getLastErrorSummary() {
        return lastErrorSummary.get();
    }

    public void setLastErrorSummary(String summary) {
        lastErrorSummary.set(summary);
    }

    public int getLastSnapshotSize() {
        return lastSnapshotSize.get();
    }

    public List<String> getLastActiveProfiles() {
        return lastActiveProfiles.get();
    }

    public boolean isInitialLoadFailed() {
        return initialLoadFailed.get() == 1;
    }

    public void setInitialLoadFailed(boolean failed) {
        initialLoadFailed.set(failed ? 1 : 0);
    }
}
