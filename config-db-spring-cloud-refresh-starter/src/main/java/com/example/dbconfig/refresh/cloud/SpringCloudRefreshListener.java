package com.example.dbconfig.refresh.cloud;

import java.util.concurrent.atomic.AtomicBoolean;

import com.example.dbconfig.core.ConfigSnapshotUpdatedEvent;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.event.EventListener;

public class SpringCloudRefreshListener {
    private final ContextRefresher contextRefresher;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    public SpringCloudRefreshListener(ContextRefresher contextRefresher) { this.contextRefresher = contextRefresher; }

    @EventListener
    public void onSnapshotUpdated(ConfigSnapshotUpdatedEvent event) {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            contextRefresher.refresh();
        } finally {
            refreshing.set(false);
        }
    }
}
