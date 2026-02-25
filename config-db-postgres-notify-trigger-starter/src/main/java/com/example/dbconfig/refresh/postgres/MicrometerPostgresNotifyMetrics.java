package com.example.dbconfig.refresh.postgres;

import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

final class MicrometerPostgresNotifyMetrics implements PostgresNotifyMetrics {

    private final Counter notifyReceived;
    private final Counter refreshTriggered;
    private final Counter reconnects;
    private final AtomicInteger connected = new AtomicInteger();

    MicrometerPostgresNotifyMetrics(MeterRegistry meterRegistry) {
        this.notifyReceived = Counter.builder("dbconfig.refresh.notify.received").register(meterRegistry);
        this.refreshTriggered = Counter.builder("dbconfig.refresh.notify.refresh.triggered").register(meterRegistry);
        this.reconnects = Counter.builder("dbconfig.refresh.notify.listener.reconnects").register(meterRegistry);
        Gauge.builder("dbconfig.refresh.notify.listener.connected", connected, AtomicInteger::doubleValue)
                .register(meterRegistry);
    }

    @Override
    public void incrementNotifyReceived() {
        notifyReceived.increment();
    }

    @Override
    public void incrementNotifyRefreshTriggered() {
        refreshTriggered.increment();
    }

    @Override
    public void incrementReconnects() {
        reconnects.increment();
    }

    @Override
    public void recordListenerConnected(boolean isConnected) {
        connected.set(isConnected ? 1 : 0);
    }
}
