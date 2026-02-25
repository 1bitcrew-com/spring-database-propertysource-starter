package com.example.dbconfig.core;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public record ConfigSnapshot(Map<String, Object> properties, ConfigVersion version, Instant fetchedAt, List<String> profilesUsed) {

    public ConfigSnapshot {
        properties = Collections.unmodifiableMap(properties == null ? Map.of() : Map.copyOf(properties));
        profilesUsed = List.copyOf(profilesUsed == null ? List.of() : profilesUsed);
    }
}
