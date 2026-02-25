package com.example.dbconfig.refresh.postgres;

interface PostgresNotifyMetrics {

    void incrementNotifyReceived();

    void incrementNotifyRefreshTriggered();

    void incrementReconnects();

    void recordListenerConnected(boolean connected);
}
