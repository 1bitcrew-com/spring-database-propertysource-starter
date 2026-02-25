package com.example.dbconfig.refresh;

import java.time.Duration;

public class NoopDbConfigRefreshMetrics implements DbConfigRefreshMetrics {

    @Override
    public void incrementPollTicks() {
    }

    @Override
    public void incrementChangesDetected() {
    }

    @Override
    public void incrementRefreshTriggered() {
    }

    @Override
    public void incrementFailures() {
    }

    @Override
    public void recordDbLastUpdatedTime(Duration duration) {
    }

    @Override
    public void recordDbLoadAllTime(Duration duration) {
    }

    @Override
    public void recordContextRefreshTime(Duration duration) {
    }
}
