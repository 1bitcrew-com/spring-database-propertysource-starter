package com.example.dbconfig.refresh;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.example.dbconfig.core.ConfigVersion;
import com.example.dbconfig.core.RefreshTrigger;
import com.example.dbconfig.core.TriggerReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

public class PollingRefreshTrigger implements RefreshTrigger, SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(PollingRefreshTrigger.class);
    private final ConfigRefreshOrchestrator orchestrator;
    private final DbConfigRefreshProperties properties;
    private final Clock clock;
    private final ScheduledExecutorService executorService;
    private volatile Instant lastRefreshAt = Instant.EPOCH;
    private volatile Instant lastSeenVersion = Instant.EPOCH;
    private volatile boolean running;

    public PollingRefreshTrigger(ConfigRefreshOrchestrator orchestrator, DbConfigRefreshProperties properties) {
        this(orchestrator, properties, Clock.systemUTC(), Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dbconfig-refresh-poller");
            t.setDaemon(true);
            return t;
        }));
    }

    PollingRefreshTrigger(ConfigRefreshOrchestrator orchestrator, DbConfigRefreshProperties properties, Clock clock, ScheduledExecutorService executorService) {
        this.orchestrator = orchestrator;
        this.properties = properties;
        this.clock = clock;
        this.executorService = executorService;
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        orchestrator.loadInitialSnapshot();
        long intervalMs = Math.max(100L, properties.getPollInterval().toMillis());
        executorService.scheduleWithFixedDelay(this::safePoll, Math.max(0L, properties.getInitialDelay().toMillis()), intervalMs, TimeUnit.MILLISECONDS);
    }

    private void safePoll() {
        try {
            Optional<ConfigVersion> current = orchestrator.fetchVersion();
            Instant now = clock.instant();
            if (current.map(ConfigVersion::instantValue).orElse(Instant.EPOCH).isAfter(lastSeenVersion)
                    && Duration.between(lastRefreshAt, now).compareTo(properties.getRefresh().getMinInterval()) >= 0) {
                orchestrator.requestRefresh(TriggerReason.POLL);
                lastRefreshAt = now;
                lastSeenVersion = current.map(ConfigVersion::instantValue).orElse(Instant.EPOCH);
            }
        } catch (Exception ex) {
            log.warn("Polling failed", ex);
        }
    }

    @Override
    public void stop() {
        running = false;
        executorService.shutdownNow();
    }

    @Override
    public boolean isRunning() { return running; }
    @Override
    public boolean isAutoStartup() { return true; }
    @Override
    public int getPhase() { return Integer.MAX_VALUE; }
    @Override
    public void stop(Runnable callback) { stop(); callback.run(); }
}
