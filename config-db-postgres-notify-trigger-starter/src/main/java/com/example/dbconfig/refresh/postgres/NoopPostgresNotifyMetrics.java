package com.example.dbconfig.refresh.postgres;

final class NoopPostgresNotifyMetrics implements PostgresNotifyMetrics {

    @Override
    public void incrementNotifyReceived() {
    }

    @Override
    public void incrementNotifyRefreshTriggered() {
    }

    @Override
    public void incrementReconnects() {
    }

    @Override
    public void recordListenerConnected(boolean connected) {
    }
}
