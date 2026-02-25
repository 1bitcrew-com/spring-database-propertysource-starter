# spring-database-propertysource-starter

## Iteration 6 modular architecture

### New modules
- `config-db-core` – domain model + SPI (`ConfigSnapshotProvider`, `RefreshTrigger`, snapshot/version types, retry util).
- `config-db-core-spring-boot-starter` – property source install/update, `ConfigRefreshOrchestrator`, polling trigger, actuator/info/health, metrics (without Spring Cloud dependency).
- `config-db-jdbc-source-spring-boot-starter` – JDBC `ConfigSnapshotProvider` implementation.
- `config-db-spring-cloud-refresh-starter` – Spring Cloud adapter listening on `ConfigSnapshotUpdatedEvent` and calling `ContextRefresher.refresh()`.
- `config-db-postgres-notify-trigger-starter` – Postgres LISTEN/NOTIFY trigger calling orchestrator (`TriggerReason.EVENT`).
- `config-db-testapp` – integration app using explicit modular dependencies.

### Backward compatibility
Compatibility artifacts are preserved:
- `db-config-refresh-spring-cloud-starter` now acts as an umbrella starter, depending on core + JDBC source + Spring Cloud refresh modules.
- `db-config-refresh-postgres-notify-starter` now acts as an umbrella starter adding postgres notify trigger module.

This preserves a migration path where existing apps can keep old artifactIds and migrate dependencies incrementally to the new modular set.
