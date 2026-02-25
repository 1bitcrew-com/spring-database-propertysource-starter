package com.example.dbconfig.refresh;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

public class DbConfigRefreshInfoContributor implements InfoContributor {

    private final DbConfigRefreshState state;
    private final DbConfigRefreshProperties properties;

    public DbConfigRefreshInfoContributor(DbConfigRefreshState state, DbConfigRefreshProperties properties) {
        this.state = state;
        this.properties = properties;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("version", state.getLastDbVersionSeen());
        details.put("lastSuccess", state.getLastSuccessRefresh());
        details.put("lastAttempt", state.getLastAttemptRefresh());
        details.put("keysCount", state.getLastSnapshotSize());
        details.put("degraded", state.isDegraded() == 1);

        if (properties.getActuator().isExposeDetails()) {
            details.put("activeProfiles", state.getLastActiveProfiles());
            details.put("consecutiveFailures", state.getConsecutiveFailures());
        }

        builder.withDetail("dbconfig", details);
    }
}
