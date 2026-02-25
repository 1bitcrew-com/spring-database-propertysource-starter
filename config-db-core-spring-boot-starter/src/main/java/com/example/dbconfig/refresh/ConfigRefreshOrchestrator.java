package com.example.dbconfig.refresh;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.example.dbconfig.core.ActiveProfiles;
import com.example.dbconfig.core.ConfigSnapshot;
import com.example.dbconfig.core.ConfigSnapshotProvider;
import com.example.dbconfig.core.ConfigSnapshotUpdatedEvent;
import com.example.dbconfig.core.ConfigVersion;
import com.example.dbconfig.core.RetryExecutor;
import com.example.dbconfig.core.TriggerReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.ConfigurableEnvironment;

public class ConfigRefreshOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(ConfigRefreshOrchestrator.class);

    private final ConfigSnapshotProvider snapshotProvider;
    private final DbConfigPropertySource propertySource;
    private final DbConfigRefreshProperties properties;
    private final ConfigurableEnvironment environment;
    private final DbConfigRefreshState state;
    private final DbConfigRefreshMetrics metrics;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ConfigRefreshOrchestrator(ConfigSnapshotProvider snapshotProvider,
            DbConfigPropertySource propertySource,
            DbConfigRefreshProperties properties,
            ConfigurableEnvironment environment,
            DbConfigRefreshState state,
            DbConfigRefreshMetrics metrics,
            ApplicationEventPublisher eventPublisher) {
        this(snapshotProvider, propertySource, properties, environment, state, metrics, eventPublisher, Clock.systemUTC());
    }

    ConfigRefreshOrchestrator(ConfigSnapshotProvider snapshotProvider,
            DbConfigPropertySource propertySource,
            DbConfigRefreshProperties properties,
            ConfigurableEnvironment environment,
            DbConfigRefreshState state,
            DbConfigRefreshMetrics metrics,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.snapshotProvider = snapshotProvider;
        this.propertySource = propertySource;
        this.properties = properties;
        this.environment = environment;
        this.state = state;
        this.metrics = metrics;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public RefreshResult requestRefresh(TriggerReason reason) {
        return refreshNow(reason, false);
    }

    public RefreshResult loadInitialSnapshot() {
        return refreshNow(TriggerReason.STARTUP, true);
    }

    public Optional<ConfigVersion> fetchVersion() {
        return withRetry("fetchVersion", snapshotProvider::fetchVersion, metrics::recordDbLastUpdatedTime);
    }

    private synchronized RefreshResult refreshNow(TriggerReason reason, boolean initialLoad) {
        Instant start = clock.instant();
        List<String> profiles = resolveProfiles();
        state.onAttempt(start, profiles);

        try {
            ConfigSnapshot snapshot = withRetry("fetchSnapshot", () -> snapshotProvider.fetchSnapshot(new ActiveProfiles(profiles)), metrics::recordDbLoadAllTime);
            Map<String, Object> propertiesMap = snapshot.properties();
            propertySource.reload(propertiesMap);
            state.onRefreshTriggered();
            metrics.incrementRefreshTriggered();
            state.onSuccess(clock.instant(), snapshot.version().instantValue(), propertySource.size());
            eventPublisher.publishEvent(new ConfigSnapshotUpdatedEvent(snapshot, reason));
            return new RefreshResult(true, propertiesMap.size(), snapshot.version().instantValue(), Duration.between(start, clock.instant()).toMillis(), reason.name(), initialLoad ? "Initial snapshot loaded" : "Refresh completed");
        } catch (RuntimeException ex) {
            handleFailure(initialLoad ? "Initial DB config load failed" : "DB config refresh failed", ex, initialLoad);
            return new RefreshResult(false, 0, state.getLastDbVersionSeen(), Duration.between(start, clock.instant()).toMillis(), reason.name(), summarize(ex));
        }
    }

    private <T> T withRetry(String operationName, Supplier<T> supplier, Consumer<Duration> timerRecorder) {
        Instant start = clock.instant();
        try {
            return RetryExecutor.execute(supplier, retryPolicy(), log, operationName);
        } finally {
            timerRecorder.accept(Duration.between(start, clock.instant()));
        }
    }

    private RetryExecutor.RetryPolicy retryPolicy() {
        DbConfigRefreshProperties.Retry retry = properties.getRetry();
        return new RetryExecutor.RetryPolicy(Math.max(1, retry.getMaxAttempts()), retry.getInitialBackoff(), retry.getMaxBackoff(), retry.getJitter());
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
        int max = properties.getFailSoftSettings().getMaxConsecutiveFailures();
        state.setDegraded(max > 0 && failures > max);
        if (!properties.isFailSoft()) {
            throw new IllegalStateException(message, ex);
        }
        log.warn("{}; fail-soft mode enabled; consecutiveFailures={}", message, failures, ex);
    }

    private String summarize(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
