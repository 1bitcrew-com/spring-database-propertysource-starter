package com.example.dbconfig.core;

import java.util.List;

public record ActiveProfiles(List<String> values) {

    public ActiveProfiles {
        values = List.copyOf(values == null ? List.of() : values);
    }
}
