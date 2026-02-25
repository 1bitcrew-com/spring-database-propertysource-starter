package com.example.dbconfig.refresh;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

public class DbConfigRefreshScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DbConfigRefreshScheduler.class);

    private final DbConfigRefreshService refreshService;
    private final DbConfigRefreshProperties properties;
    private final DbConfigRefreshState state;
    private final DbConfigRefreshMetrics metrics;
    private final Clock clock;
    private final ScheduledExecutorService executorService;

    private volatile Instant lastSeenUpdate = Instant.EPOCH;
    private volatile Instant lastRefreshAt = Instant.EPOCH;
    private volatile Instant firstChangeDetectedAt;
    private volatile Instant pendingRefreshAt;
    private volatile boolean refreshScheduled;
    private volatile boolean running;

    public DbConfigRefreshScheduler(DbConfigRefreshService refreshService,
            DbConfigRefreshProperties properties,
            DbConfigRefreshState state,
            DbConfigRefreshMetrics metrics) {
        this(refreshService,
                properties,
                state,
                metrics,
                Clock.systemUTC(),
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread thread = new Thread(r, "dbconfig-refresh-scheduler");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    DbConfigRefreshScheduler(DbConfigRefreshService refreshService,
            DbConfigRefreshProperties properties,
            DbConfigRefreshState state,
            DbConfigRefreshMetrics metrics,
            Clock clock,
            ScheduledExecutorService executorService) {
        this.refreshService = refreshService;
        this.properties = properties;
        this.state = state;
        this.metrics = metrics;
        this.clock = clock;
        this.executorService = executorService;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }

        running = true;
        try {
            RefreshResult initial = refreshService.loadInitialSnapshot();
            if (initial.refreshed()) {
                lastSeenUpdate = initial.version();
                lastRefreshAt = clock.instant();
            }
        }
        catch (RuntimeException ex) {
            running = false;
            throw ex;
        }

        long intervalMs = Math.max(100L, properties.getPollInterval().toMillis());
        long initialDelayMs = Math.max(0L, properties.getInitialDelay().toMillis());
        executorService.scheduleWithFixedDelay(this::safePollForChanges, initialDelayMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("Started DB config refresh scheduler; interval={}ms, initialDelay={}ms", intervalMs, initialDelayMs);
    }

    @Override
    public void stop() {
        running = false;
        executorService.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    private void safePollForChanges() {
        if (!running) {
            return;
        }
        try {
            pollForChanges();
        }
        catch (RuntimeException ex) {
            log.warn("Polling DB config failed", ex);
        }
    }

    private void pollForChanges() {
        metrics.incrementPollTicks();
        Instant now = clock.instant();

        Instant currentVersion = refreshService.readCurrentDbVersion();
        if (currentVersion.isAfter(lastSeenUpdate)) {
            metrics.incrementChangesDetected();
            registerDetectedChange(now);
        }

        if (!shouldRunRefresh(now)) {
            return;
        }

        RefreshResult result = refreshService.refreshNow(RefreshTrigger.SCHEDULED);
        if (result.refreshed()) {
            lastSeenUpdate = result.version();
            lastRefreshAt = clock.instant();
            clearPendingRefresh();
        }
        if (properties.getFailFast().getMode() == DbConfigRefreshProperties.FailFast.Mode.STOP_SCHEDULER
                && state.getConsecutiveFailures() > 0
                && !properties.isFailSoft()) {
            stop();
        }
    }

    private synchronized void registerDetectedChange(Instant now) {
        if (firstChangeDetectedAt == null) {
            firstChangeDetectedAt = now;
        }
        Instant coalesced = now.plus(properties.getRefresh().getCoalesceWindow());
        if (!refreshScheduled || pendingRefreshAt == null || coalesced.isAfter(pendingRefreshAt)) {
            pendingRefreshAt = coalesced;
        }
        refreshScheduled = true;
    }

    private synchronized boolean shouldRunRefresh(Instant now) {
        if (!refreshScheduled || pendingRefreshAt == null) {
            return false;
        }

        Instant earliestByMinInterval = lastRefreshAt.plus(properties.getRefresh().getMinInterval());
        Instant dueAt = maxInstant(pendingRefreshAt, earliestByMinInterval);
        if (firstChangeDetectedAt != null) {
            Instant forcedAt = firstChangeDetectedAt.plus(properties.getRefresh().getMaxWait());
            dueAt = minInstant(dueAt, forcedAt);
        }

        if (now.isBefore(dueAt)) {
            pendingRefreshAt = dueAt;
            return false;
        }
        return true;
    }

    private void clearPendingRefresh() {
        synchronized (this) {
            firstChangeDetectedAt = null;
            pendingRefreshAt = null;
            refreshScheduled = false;
        }
    }

    private static Instant maxInstant(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant minInstant(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
