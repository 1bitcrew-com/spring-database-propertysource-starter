package com.example.dbconfig.refresh;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

@Endpoint(id = "dbconfigrefresh")
public class DbConfigRefreshEndpoint {

    private final DbConfigRefreshService refreshService;
    private final DbConfigRefreshState state;
    private final DbConfigRefreshProperties properties;

    public DbConfigRefreshEndpoint(DbConfigRefreshService refreshService,
            DbConfigRefreshState state,
            DbConfigRefreshProperties properties) {
        this.refreshService = refreshService;
        this.state = state;
        this.properties = properties;
    }

    @ReadOperation
    public Map<String, Object> status() {
        Map<String, Object> body = baseMetadata();
        body.put("currentDbVersion", state.getLastDbVersionSeen());
        body.put("refreshTriggeredCount", state.getRefreshTriggeredCount());
        return body;
    }

    @WriteOperation
    public Map<String, Object> refresh() {
        RefreshResult result = refreshService.refreshNow(RefreshTrigger.MANUAL);
        Map<String, Object> body = baseMetadata();
        body.put("refreshed", result.refreshed());
        body.put("changedKeysCount", result.changedKeysCount());
        body.put("newVersion", result.version());
        body.put("durationMs", result.durationMs());
        body.put("trigger", result.trigger());
        body.put("message", result.message());
        return body;
    }

    private Map<String, Object> baseMetadata() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lastSuccessfulRefresh", state.getLastSuccessRefresh());
        body.put("lastAttempt", state.getLastAttemptRefresh());
        body.put("lastError", state.getLastErrorSummary());
        body.put("snapshotKeys", state.getLastSnapshotSize());
        body.put("degraded", state.isDegraded() == 1);
        body.put("notifyEnabled", state.isNotifyEnabled());
        body.put("notifyChannel", state.getNotifyChannel());
        body.put("notifyListenerConnected", state.isListenConnected());
        body.put("lastNotifyAt", toNullableInstant(state.getLastNotifyAt()));
        if (properties.getActuator().isExposeDetails()) {
            body.put("activeProfiles", state.getLastActiveProfiles());
            body.put("initialLoadFailed", state.isInitialLoadFailed());
        }
        return body;
    }
    private Instant toNullableInstant(Instant value) {
        return (value == null || Instant.EPOCH.equals(value)) ? null : value;
    }

}
