package com.example.dbconfig.refresh;

import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;

public class DbConfigRefreshHealthIndicator implements HealthIndicator {

    private static final Status DEGRADED = new Status("DEGRADED");

    private final DbConfigRefreshState state;
    private final DbConfigRefreshProperties properties;

    public DbConfigRefreshHealthIndicator(DbConfigRefreshState state, DbConfigRefreshProperties properties) {
        this.state = state;
        this.properties = properties;
    }

    @Override
    public Health health() {
        Status status = resolveStatus();
        Health.Builder builder = Health.status(status)
                .withDetail("lastSuccess", state.getLastSuccessRefresh())
                .withDetail("lastAttempt", state.getLastAttemptRefresh())
                .withDetail("dbVersion", state.getLastDbVersionSeen())
                .withDetail("consecutiveFailures", state.getConsecutiveFailures())
                .withDetail("failMode", properties.isFailSoft() ? "SOFT" : "FAST");

        if (state.getLastErrorSummary() != null) {
            builder.withDetail("lastError", state.getLastErrorSummary());
        }
        if (properties.getActuator().isExposeDetails()) {
            builder.withDetail("activeProfiles", state.getLastActiveProfiles());
            builder.withDetail("degraded", state.isDegraded() == 1);
        }

        return builder.build();
    }

    private Status resolveStatus() {
        if (state.isInitialLoadFailed() && isFailFastInitial()) {
            return Status.DOWN;
        }
        if (state.isDegraded() == 1) {
            return DEGRADED;
        }
        if (state.getConsecutiveFailures() > 0) {
            return Status.OUT_OF_SERVICE;
        }
        Duration staleAfter = properties.getActuator().getHealth().getStaleAfter();
        if (staleAfter != null
                && state.getLastSuccessRefresh() != null
                && state.getLastSuccessRefresh().isAfter(Instant.EPOCH)
                && state.getLastSuccessRefresh().plus(staleAfter).isBefore(Instant.now())) {
            return Status.UNKNOWN;
        }
        return Status.UP;
    }

    private boolean isFailFastInitial() {
        Boolean configured = properties.getFailFast().getOnInitialLoad();
        if (configured != null) {
            return configured;
        }
        return !properties.isFailSoft();
    }
}
