package com.example.dbconfig.refresh;

import java.time.Duration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

public class MicrometerDbConfigRefreshMetrics implements DbConfigRefreshMetrics {

    private final Counter pollTicks;
    private final Counter changesDetected;
    private final Counter refreshTriggered;
    private final Counter failures;
    private final Timer dbLastUpdatedTimer;
    private final Timer dbLoadAllTimer;
    private final Timer contextRefreshTimer;

    public MicrometerDbConfigRefreshMetrics(MeterRegistry meterRegistry,
            DbConfigRefreshState runtimeState,
            String propertySourceName,
            String activeProfilesTag,
            String failMode,
            boolean profileTagEnabled) {
        Tags tags = Tags.of("propertySource", propertySourceName, "failMode", failMode);
        if (profileTagEnabled) {
            tags = tags.and("activeProfiles", activeProfilesTag);
        }

        this.pollTicks = meterRegistry.counter("dbconfig.refresh.poll.ticks", tags);
        this.changesDetected = meterRegistry.counter("dbconfig.refresh.changes.detected", tags);
        this.refreshTriggered = meterRegistry.counter("dbconfig.refresh.refresh.triggered", tags);
        this.failures = meterRegistry.counter("dbconfig.refresh.failures", tags);

        this.dbLastUpdatedTimer = meterRegistry.timer("dbconfig.refresh.db.lastUpdated.time", tags);
        this.dbLoadAllTimer = meterRegistry.timer("dbconfig.refresh.db.loadAll.time", tags);
        this.contextRefreshTimer = meterRegistry.timer("dbconfig.refresh.contextRefresh.time", tags);

        Gauge.builder("dbconfig.refresh.last.success.epoch", runtimeState, s -> s.getLastSuccessEpochMillis() / 1000.0d)
                .tags(tags)
                .register(meterRegistry);
        Gauge.builder("dbconfig.refresh.consecutive.failures", runtimeState, DbConfigRefreshState::getConsecutiveFailures)
                .tags(tags)
                .register(meterRegistry);
        Gauge.builder("dbconfig.refresh.degraded", runtimeState, DbConfigRefreshState::isDegraded)
                .tags(tags)
                .register(meterRegistry);
    }

    @Override
    public void incrementPollTicks() {
        pollTicks.increment();
    }

    @Override
    public void incrementChangesDetected() {
        changesDetected.increment();
    }

    @Override
    public void incrementRefreshTriggered() {
        refreshTriggered.increment();
    }

    @Override
    public void incrementFailures() {
        failures.increment();
    }

    @Override
    public void recordDbLastUpdatedTime(Duration duration) {
        dbLastUpdatedTimer.record(duration);
    }

    @Override
    public void recordDbLoadAllTime(Duration duration) {
        dbLoadAllTimer.record(duration);
    }

    @Override
    public void recordContextRefreshTime(Duration duration) {
        contextRefreshTimer.record(duration);
    }
}
