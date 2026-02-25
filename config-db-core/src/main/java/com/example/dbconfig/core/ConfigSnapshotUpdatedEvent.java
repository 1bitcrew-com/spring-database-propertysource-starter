package com.example.dbconfig.core;

public record ConfigSnapshotUpdatedEvent(ConfigSnapshot snapshot, TriggerReason reason) {
}
