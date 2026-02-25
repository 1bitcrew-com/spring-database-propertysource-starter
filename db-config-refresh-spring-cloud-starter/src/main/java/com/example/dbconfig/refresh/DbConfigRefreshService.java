package com.example.dbconfig.refresh;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.core.env.ConfigurableEnvironment;

public class DbConfigRefreshService {

    private static final Logger log = LoggerFactory.getLogger(DbConfigRefreshService.class);

    private final DbConfigJdbcRepository repository;
    private final DbConfigPropertySource propertySource;
    private final ContextRefresher contextRefresher;
    private final DbConfigRefreshProperties properties;
    private final ConfigurableEnvironment environment;
    private final DbConfigRefreshState state;
    private final DbConfigRefreshMetrics metrics;
    private final Clock clock;

    public DbConfigRefreshService(DbConfigJdbcRepository repository,
            DbConfigPropertySource propertySource,
            ContextRefresher contextRefresher,
            DbConfigRefreshProperties properties,
            ConfigurableEnvironment environment,
            DbConfigRefreshState state,
            DbConfigRefreshMetrics metrics) {
        this(repository, propertySource, contextRefresher, properties, environment, state, metrics, Clock.systemUTC());
    }

    DbConfigRefreshService(DbConfigJdbcRepository repository,
            DbConfigPropertySource propertySource,
            ContextRefresher contextRefresher,
            DbConfigRefreshProperties properties,
            ConfigurableEnvironment environment,
            DbConfigRefreshState state,
            DbConfigRefreshMetrics metrics,
            Clock clock) {
        this.repository = repository;
        this.propertySource = propertySource;
        this.contextRefresher = contextRefresher;
        this.properties = properties;
        this.environment = environment;
        this.state = state;
        this.metrics = metrics;
        this.clock = clock;
    }

    public RefreshResult loadInitialSnapshot() {
        return refreshNow(RefreshTrigger.SCHEDULED, true);
    }

    public RefreshResult refreshNow(RefreshTrigger trigger) {
        return refreshNow(trigger, false);
    }

    private synchronized RefreshResult refreshNow(RefreshTrigger trigger, boolean initialLoad) {
        Instant start = clock.instant();
        List<String> profiles = resolveProfiles();
        state.onAttempt(start, profiles);

        try {
            Instant version = withRetry("getLastUpdated", repository::getLastUpdated, metrics::recordDbLastUpdatedTime);
            Map<String, Object> propertiesMap = withRetry(
                    "loadMergedForProfiles",
                    () -> repository.loadMergedForProfiles(profiles),
                    metrics::recordDbLoadAllTime);

            int previousSize = propertySource.size();
            propertySource.reload(propertiesMap);

            state.onRefreshTriggered();
            metrics.incrementRefreshTriggered();

            Instant refreshStart = clock.instant();
            Set<String> changed = contextRefresher.refresh();
            metrics.recordContextRefreshTime(Duration.between(refreshStart, clock.instant()));

            state.onSuccess(clock.instant(), version, propertySource.size());

            int changedCount = changed != null ? changed.size() : Math.max(0, propertySource.size() - previousSize);
            return new RefreshResult(true,
                    changedCount,
                    version,
                    Duration.between(start, clock.instant()).toMillis(),
                    trigger.name(),
                    initialLoad ? "Initial snapshot loaded" : "Refresh completed");
        }
        catch (RuntimeException ex) {
            handleFailure(initialLoad ? "Initial DB config load failed" : "DB config refresh failed", ex, initialLoad);
            return new RefreshResult(false,
                    0,
                    state.getLastDbVersionSeen(),
                    Duration.between(start, clock.instant()).toMillis(),
                    trigger.name(),
                    summarize(ex));
        }
    }

    public Instant readCurrentDbVersion() {
        return withRetry("getLastUpdated", repository::getLastUpdated, metrics::recordDbLastUpdatedTime);
    }

    private <T> T withRetry(String operationName, Supplier<T> supplier, Consumer<Duration> timerRecorder) {
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

    private void handleFailure(String message, RuntimeException ex, boolean initialLoad) {
        metrics.incrementFailures();
        int failures = state.incrementFailures();
        state.setLastErrorSummary(summarize(ex));
        if (initialLoad) {
            state.setInitialLoadFailed(true);
        }
        updateDegradedState(failures);

        if (isFailFastInitialLoad(initialLoad)) {
            throw new IllegalStateException(message, ex);
        }

        DbConfigRefreshProperties.FailFast.Mode mode = properties.getFailFast().getMode();
        if (!properties.isFailSoft() && mode == DbConfigRefreshProperties.FailFast.Mode.NONE) {
            throw new IllegalStateException(message, ex);
        }
        if (!initialLoad && mode == DbConfigRefreshProperties.FailFast.Mode.FAIL_APPLICATION) {
            throw new IllegalStateException(message, ex);
        }

        log.warn("{}; fail-soft mode enabled; consecutiveFailures={}", message, failures, ex);
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
        state.setDegraded(max > 0 && failures > max);
    }

    private String summarize(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
