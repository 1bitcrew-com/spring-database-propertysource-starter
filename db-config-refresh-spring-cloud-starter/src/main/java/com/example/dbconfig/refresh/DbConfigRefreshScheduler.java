package com.example.dbconfig.refresh;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.ConfigurableEnvironment;

public class DbConfigRefreshScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DbConfigRefreshScheduler.class);
    private static final Duration DEGRADED_LOG_PERIOD = Duration.ofMinutes(1);

    private final DbConfigJdbcRepository repository;
    private final DbConfigPropertySource propertySource;
    private final ContextRefresher contextRefresher;
    private final DbConfigRefreshProperties properties;
    private final ConfigurableEnvironment environment;
    private final DbConfigRefreshRuntimeState runtimeState;
    private final DbConfigRefreshMetrics metrics;
    private final Clock clock;
    private final ScheduledExecutorService executorService;

    private volatile Instant lastSeenUpdate = Instant.EPOCH;
    private volatile Instant lastRefreshAt = Instant.EPOCH;
    private volatile Instant firstChangeDetectedAt;
    private volatile Instant pendingRefreshAt;
    private volatile Instant lastDegradedLogAt = Instant.EPOCH;
    private volatile boolean refreshScheduled;
    private volatile boolean running;

    public DbConfigRefreshScheduler(DbConfigJdbcRepository repository,
            DbConfigPropertySource propertySource,
            ContextRefresher contextRefresher,
            DbConfigRefreshProperties properties,
            ConfigurableEnvironment environment,
            DbConfigRefreshRuntimeState runtimeState,
            DbConfigRefreshMetrics metrics) {
        this(repository,
                propertySource,
                contextRefresher,
                properties,
                environment,
                runtimeState,
                metrics,
                Clock.systemUTC(),
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread thread = new Thread(r, "dbconfig-refresh-scheduler");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    DbConfigRefreshScheduler(DbConfigJdbcRepository repository,
            DbConfigPropertySource propertySource,
            ContextRefresher contextRefresher,
            DbConfigRefreshProperties properties,
            ConfigurableEnvironment environment,
            DbConfigRefreshRuntimeState runtimeState,
            DbConfigRefreshMetrics metrics,
            Clock clock,
            ScheduledExecutorService executorService) {
        this.repository = repository;
        this.propertySource = propertySource;
        this.contextRefresher = contextRefresher;
        this.properties = properties;
        this.environment = environment;
        this.runtimeState = runtimeState;
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
            loadSnapshot(true);
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
            handleRuntimeFailure("Polling DB config failed", ex);
        }
    }

    private void pollForChanges() {
        metrics.incrementPollTicks();
        Instant now = clock.instant();

        Instant currentVersion = withRetry("getLastUpdated", repository::getLastUpdated, metrics::recordDbLastUpdatedTime);
        if (currentVersion.isAfter(lastSeenUpdate)) {
            metrics.incrementChangesDetected();
            registerDetectedChange(now);
        }

        if (!shouldRunRefresh(now)) {
            return;
        }

        doRefresh(currentVersion);
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

    private void doRefresh(Instant currentVersion) {
        List<String> profiles = resolveProfiles();
        Map<String, Object> propertiesMap = withRetry(
                "loadMergedForProfiles",
                () -> repository.loadMergedForProfiles(profiles),
                metrics::recordDbLoadAllTime);

        propertySource.reload(propertiesMap);
        lastSeenUpdate = currentVersion;
        lastRefreshAt = clock.instant();
        clearPendingRefresh();

        metrics.incrementRefreshTriggered();
        runtimeState.onRefreshTriggered();

        Instant refreshStart = clock.instant();
        contextRefresher.refresh();
        metrics.recordContextRefreshTime(Duration.between(refreshStart, clock.instant()));

        runtimeState.onSuccess();
        log.info("Refreshed DB config: keys={}, profiles={}, version={}", propertySource.size(), profiles, currentVersion);
    }

    private void clearPendingRefresh() {
        synchronized (this) {
            firstChangeDetectedAt = null;
            pendingRefreshAt = null;
            refreshScheduled = false;
        }
    }

    private void loadSnapshot(boolean initialLoad) {
        try {
            List<String> profiles = resolveProfiles();
            Instant version = withRetry("getLastUpdated", repository::getLastUpdated, metrics::recordDbLastUpdatedTime);
            Map<String, Object> propertiesMap = withRetry(
                    "loadMergedForProfiles",
                    () -> repository.loadMergedForProfiles(profiles),
                    metrics::recordDbLoadAllTime);

            propertySource.reload(propertiesMap);
            lastSeenUpdate = version;
            lastRefreshAt = clock.instant();
            runtimeState.onSuccess();
            log.info("Loaded DB config snapshot: keys={}, profiles={}, version={}", propertySource.size(), profiles, version);
        }
        catch (RuntimeException ex) {
            handleFailure("Initial DB config load failed", ex, initialLoad);
        }
    }

    private <T> T withRetry(String operationName, java.util.function.Supplier<T> supplier,
            java.util.function.Consumer<Duration> timerRecorder) {
        Instant start = clock.instant();
        try {
            return RetryExecutor.execute(supplier, retryPolicy(), log, operationName);
        }
        finally {
            timerRecorder.accept(Duration.between(start, clock.instant()));
        }
    }

    private RetryExecutor.RetryPolicy retryPolicy() {
        DbConfigRefreshProperties.Retry retry = properties.getRetry();
        return new RetryExecutor.RetryPolicy(
                Math.max(1, retry.getMaxAttempts()),
                retry.getInitialBackoff(),
                retry.getMaxBackoff(),
                retry.getJitter());
    }

    private List<String> resolveProfiles() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            return Arrays.asList(activeProfiles);
        }
        return Arrays.asList(environment.getDefaultProfiles());
    }

    private void handleRuntimeFailure(String message, RuntimeException ex) {
        handleFailure(message, ex, false);
    }

    private void handleFailure(String message, RuntimeException ex, boolean initialLoad) {
        metrics.incrementFailures();
        int failures = runtimeState.incrementFailures();
        updateDegradedState(failures);

        if (isFailFastInitialLoad(initialLoad)) {
            throw new IllegalStateException(message, ex);
        }

        DbConfigRefreshProperties.FailFast.Mode mode = properties.getFailFast().getMode();
        if (!properties.isFailSoft() && mode == DbConfigRefreshProperties.FailFast.Mode.NONE) {
            throw new IllegalStateException(message, ex);
        }

        if (!initialLoad && mode == DbConfigRefreshProperties.FailFast.Mode.STOP_SCHEDULER) {
            log.error("{}; stopping scheduler due to fail-fast.mode=STOP_SCHEDULER", message, ex);
            stop();
            return;
        }
        if (!initialLoad && mode == DbConfigRefreshProperties.FailFast.Mode.FAIL_APPLICATION) {
            log.error("{}; fail-fast.mode=FAIL_APPLICATION", message, ex);
            throw new IllegalStateException(message, ex);
        }

        logFailureWithRateLimit(message, ex, failures);
    }

    private boolean isFailFastInitialLoad(boolean initialLoad) {
        if (!initialLoad) {
            return false;
        }
        Boolean configured = properties.getFailFast().getOnInitialLoad();
        if (configured != null) {
            return configured;
        }
        return !properties.isFailSoft();
    }

    private void updateDegradedState(int failures) {
        int max = properties.getFailSoftSettings().getMaxConsecutiveFailures();
        if (max > 0 && failures > max) {
            runtimeState.setDegraded(true);
            return;
        }
        runtimeState.setDegraded(false);
    }

    private void logFailureWithRateLimit(String message, RuntimeException ex, int failures) {
        if (runtimeState.isDegraded() == 1) {
            Instant now = clock.instant();
            if (Duration.between(lastDegradedLogAt, now).compareTo(DEGRADED_LOG_PERIOD) >= 0) {
                lastDegradedLogAt = now;
                log.warn("{}; fail-soft mode enabled; consecutiveFailures={}, degraded=true", message, failures, ex);
            }
            else {
                log.debug("{}; suppressed degraded-mode log; consecutiveFailures={}", message, failures);
            }
            return;
        }
        log.warn("{}; fail-soft mode enabled; consecutiveFailures={}", message, failures, ex);
    }

    private static Instant maxInstant(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant minInstant(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
