package com.example.dbconfig.core;

import java.util.Optional;

public interface ConfigSnapshotProvider {

    Optional<ConfigVersion> fetchVersion();

    ConfigSnapshot fetchSnapshot(ActiveProfiles profiles);
}
