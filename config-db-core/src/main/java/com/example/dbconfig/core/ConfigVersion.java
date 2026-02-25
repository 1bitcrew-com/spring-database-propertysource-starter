package com.example.dbconfig.core;

import java.time.Instant;
import java.util.Objects;

public record ConfigVersion(String value, Instant instantValue) implements Comparable<ConfigVersion> {

    public static ConfigVersion ofInstant(Instant instant) {
        return new ConfigVersion(instant == null ? Instant.EPOCH.toString() : instant.toString(), instant == null ? Instant.EPOCH : instant);
    }

    public ConfigVersion {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public int compareTo(ConfigVersion other) {
        if (other == null) {
            return 1;
        }
        if (instantValue != null && other.instantValue != null) {
            return instantValue.compareTo(other.instantValue);
        }
        return value.compareTo(other.value);
    }
}
