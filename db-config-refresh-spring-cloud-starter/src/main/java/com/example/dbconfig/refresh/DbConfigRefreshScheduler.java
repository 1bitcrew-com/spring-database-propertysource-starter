package com.example.dbconfig.refresh;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.core.env.ConfigurableEnvironment;

public class DbConfigRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(DbConfigRefreshScheduler.class);

    private final DbConfigJdbcRepository repository;
    private final DbConfigPropertySource propertySource;
    private final ContextRefresher contextRefresher;
    private final DbConfigRefreshProperties properties;
    private final ConfigurableEnvironment environment;

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "db-config-refresh-poller");
        thread.setDaemon(true);
        return thread;
    });

    private volatile Instant lastSeenUpdate = Instant.EPOCH;
    private volatile Instant lastRefreshAt = Instant.EPOCH;

    public DbConfigRefreshScheduler(DbConfigJdbcRepository repository,
            DbConfigPropertySource propertySource,
            ContextRefresher contextRefresher,
            DbConfigRefreshProperties properties,
            ConfigurableEnvironment environment) {
        this.repository = repository;
        this.propertySource = propertySource;
        this.contextRefresher = contextRefresher;
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    public void start() {
        initialLoad();
        long intervalMs = Math.max(250L, properties.getPollInterval().toMillis());
        executorService.scheduleWithFixedDelay(this::pollForChanges, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        executorService.shutdownNow();
    }

    private void initialLoad() {
        try {
            List<String> profiles = resolveProfiles();
            lastSeenUpdate = repository.getLastUpdated();
            Map<String, Object> propertiesMap = repository.loadMergedForProfiles(profiles);
            propertySource.reload(propertiesMap);
            log.info("Loaded DB config: keys={}, profiles={}, version={}", propertySource.size(), profiles, lastSeenUpdate);
        }
        catch (Exception ex) {
            handleError("Initial DB config load failed", ex);
        }
    }

    private void pollForChanges() {
        try {
            Instant currentVersion = repository.getLastUpdated();
            if (!currentVersion.isAfter(lastSeenUpdate)) {
                return;
            }
            if (!canRefreshNow()) {
                return;
            }

            List<String> profiles = resolveProfiles();
            Map<String, Object> propertiesMap = repository.loadMergedForProfiles(profiles);
            propertySource.reload(propertiesMap);
            lastSeenUpdate = currentVersion;
            lastRefreshAt = Instant.now();
            contextRefresher.refresh();
            log.info("Refreshed DB config: keys={}, profiles={}, version={}", propertySource.size(), profiles, currentVersion);
        }
        catch (Exception ex) {
            handleError("Polling DB config failed", ex);
        }
    }

    private List<String> resolveProfiles() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            return Arrays.asList(activeProfiles);
        }
        return Arrays.asList(environment.getDefaultProfiles());
    }

    private boolean canRefreshNow() {
        Duration debounce = Duration.ofSeconds(2);
        return lastRefreshAt.plus(debounce).isBefore(Instant.now());
    }

    private void handleError(String message, Exception ex) {
        if (properties.isFailSoft()) {
            log.warn("{}; fail-soft mode enabled", message, ex);
            return;
        }
        throw new IllegalStateException(message, ex);
    }
}
