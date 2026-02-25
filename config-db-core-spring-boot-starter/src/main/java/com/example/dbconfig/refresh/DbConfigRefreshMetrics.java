package com.example.dbconfig.refresh;

import java.time.Duration;

public interface DbConfigRefreshMetrics {

    void incrementPollTicks();

    void incrementChangesDetected();

    void incrementRefreshTriggered();

    void incrementFailures();

    void recordDbLastUpdatedTime(Duration duration);

    void recordDbLoadAllTime(Duration duration);

    void recordContextRefreshTime(Duration duration);
}
