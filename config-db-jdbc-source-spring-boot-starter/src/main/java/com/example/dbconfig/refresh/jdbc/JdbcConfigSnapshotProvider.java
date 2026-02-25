package com.example.dbconfig.refresh.jdbc;

import java.time.Instant;
import java.util.Optional;

import com.example.dbconfig.core.ActiveProfiles;
import com.example.dbconfig.core.ConfigSnapshot;
import com.example.dbconfig.core.ConfigSnapshotProvider;
import com.example.dbconfig.core.ConfigVersion;

public class JdbcConfigSnapshotProvider implements ConfigSnapshotProvider {
    private final DbConfigJdbcRepository repository;
    public JdbcConfigSnapshotProvider(DbConfigJdbcRepository repository) { this.repository = repository; }
    @Override
    public Optional<ConfigVersion> fetchVersion() {
        Instant instant = repository.getLastUpdated();
        return Optional.of(ConfigVersion.ofInstant(instant));
    }
    @Override
    public ConfigSnapshot fetchSnapshot(ActiveProfiles profiles) {
        Instant version = repository.getLastUpdated();
        return new ConfigSnapshot(repository.loadMergedForProfiles(profiles.values()), ConfigVersion.ofInstant(version), Instant.now(), profiles.values());
    }
}
