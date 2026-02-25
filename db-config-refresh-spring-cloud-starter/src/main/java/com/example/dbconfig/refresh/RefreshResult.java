package com.example.dbconfig.refresh;

import java.time.Instant;

public record RefreshResult(
        boolean refreshed,
        int changedKeysCount,
        Instant version,
        long durationMs,
        String trigger,
        String message) {
}
