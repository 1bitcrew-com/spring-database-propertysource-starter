package com.example.dbconfig.refresh;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.refresh.ContextRefresher;

public class DbConfigRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(DbConfigRefreshScheduler.class);

    private final DbConfigJdbcRepository repository;
    private final DbConfigPropertySource propertySource;
    private final ContextRefresher contextRefresher;
    private final DbConfigRefreshProperties properties;

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
            DbConfigRefreshProperties properties) {
        this.repository = repository;
        this.propertySource = propertySource;
        this.contextRefresher = contextRefresher;
        this.properties = properties;
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
            lastSeenUpdate = repository.getLastUpdated();
            Map<String, Object> propertiesMap = repository.loadAll();
            propertySource.reload(propertiesMap);
            log.info("Loaded DB config: keys={}, version={}", propertySource.size(), lastSeenUpdate);
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

            Map<String, Object> propertiesMap = repository.loadAll();
            propertySource.reload(propertiesMap);
            lastSeenUpdate = currentVersion;
            lastRefreshAt = Instant.now();
            contextRefresher.refresh();
            log.info("Refreshed DB config: keys={}, version={}", propertySource.size(), currentVersion);
        }
        catch (Exception ex) {
            handleError("Polling DB config failed", ex);
        }
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
