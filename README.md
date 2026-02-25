# Spring Boot Database Config Refresh

Database-backed configuration for Spring Boot applications, with optional runtime refresh via Spring Cloud and event-driven refresh via PostgreSQL `LISTEN/NOTIFY`.

Designed for teams that want dynamic configuration updates without introducing a full external configuration server.

## Overview

This project loads key/value properties from a database table into the Spring `Environment` as a dynamic `PropertySource`, then keeps that snapshot up to date at runtime.

### What problem it solves

In many systems, some configuration values need to change without restarting applications (feature flags, partner limits, kill switches, rollout toggles, tenant-level values). File-only config requires redeploys; ad-hoc DB reads spread config logic across the codebase.

This project provides a single, framework-integrated path:

- read config from DB,
- merge it with profile semantics,
- expose it to normal Spring binding (`@Value`, `@ConfigurationProperties`),
- refresh it safely when data changes.

### Why database-backed config

Database-backed config is useful when:

- operations teams already manage runtime values in SQL,
- audit/history can be handled at the DB layer,
- changes should be visible quickly across instances,
- a lightweight solution is preferred over a full config server.

### How it integrates with Spring Boot

- Auto-configuration registers a DB-backed `PropertySource`.
- A refresh service reloads config and invokes Spring Cloud `ContextRefresher`.
- Polling and PostgreSQL notify can trigger refresh.
- Actuator endpoint/health/info expose runtime status.
- Micrometer metrics are emitted when a `MeterRegistry` is available.

### When to use it vs when not to

Use it when:

- your configuration is relatively small and key/value oriented,
- you need runtime refresh with bounded complexity,
- PostgreSQL is available (for notify mode) or polling is acceptable.

Do **not** use it when:

- you need full config lifecycle management across many services/environments (versioned repos, approvals, rollbacks, encryption workflows),
- strict distributed coordination/consensus on config application is required,
- configuration is large, hierarchical, and frequently changing at very high rates.

## Features

- Database-backed `PropertySource` loaded from `db_config_properties`
- Profile-aware property resolution (`NULL` profile + active profile overrides)
- Runtime refresh through Spring Cloud `ContextRefresher`
- Event-driven refresh using PostgreSQL `LISTEN/NOTIFY`
- Polling trigger as baseline or fallback
- Actuator integration (custom endpoint, health, info)
- Micrometer metrics (refresh, failures, DB timings, notify listener)
- Resilience controls (retry/backoff/jitter, fail-soft/fail-fast, debounce/coalescing)
- Modular architecture (refresh starter + optional Postgres notify starter)

## Architecture

At a high level, the runtime flow is:

```text
Database (db_config_properties)
   │
   ▼
ConfigSnapshotProvider (JDBC implementation)
   │
   ▼
ConfigRefreshOrchestrator (refresh service)
   │                ▲
   │                │
PropertySource      Triggers (Polling / Postgres NOTIFY / Manual actuator)
   │
   ▼
Spring Environment
   │
   ▼
@Value / @ConfigurationProperties
   │
   ▼
Spring Cloud ContextRefresher (runtime rebinding)
```

### Separation of concerns

- **Snapshot provider (source layer):** reads current DB version (`MAX(updated_at)`) and merged key/value snapshot.
- **Orchestrator (refresh layer):** compares versions, applies debounce/retry/fail-mode logic, reloads `PropertySource`, triggers context refresh.
- **Triggers:** decide *when* refresh is requested (polling schedule, notify listener, manual actuator call).
- **Spring Cloud adapter:** propagates changed property values into refresh-aware beans.

In this repository, these responsibilities are primarily implemented by:

- `DbConfigJdbcRepository` (JDBC snapshot reads)
- `DbConfigRefreshService` (orchestration)
- `DbConfigRefreshScheduler` + PostgreSQL listener (triggers)
- `ContextRefresher` integration in the refresh service

## Modules

| Module | Purpose | Required? |
|---|---|---|
| `db-config-refresh-spring-cloud-starter` | Main starter: JDBC source, dynamic `PropertySource`, polling refresh, Spring Cloud refresh integration, actuator, metrics | Yes |
| `db-config-refresh-postgres-notify-starter` | Optional PostgreSQL `LISTEN/NOTIFY` trigger with reconnect/dedupe and optional polling fallback | Optional |
| `db-config-refresh-spring-cloud-starter-testapp` | Integration test application for end-to-end behavior verification | No (tests/dev only) |

### Recommended combinations

- **Minimal (no extra trigger):** `db-config-refresh-spring-cloud-starter` with polling enabled.
- **With Spring Cloud refresh:** included by default in `db-config-refresh-spring-cloud-starter`.
- **With Postgres event-driven refresh:** add `db-config-refresh-postgres-notify-starter`; keep polling as fallback or disable fallback explicitly.

## Installation

> Use your published coordinates. The examples below use coordinates from this repository and a placeholder release version.

### Maven: core starter (JDBC + Spring Cloud refresh)

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>db-config-refresh-spring-cloud-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Maven: PostgreSQL notify trigger (optional)

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>db-config-refresh-postgres-notify-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Spring Cloud BOM (recommended)

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-dependencies</artifactId>
      <version>2023.0.3</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

## Database Schema

```sql
CREATE TABLE db_config_properties (
  prop_key      VARCHAR(255) NOT NULL,
  profile       VARCHAR(100),
  prop_value    TEXT,
  updated_at    TIMESTAMP NOT NULL DEFAULT now(),
  PRIMARY KEY (prop_key, profile)
);

CREATE INDEX idx_db_config_properties_updated_at ON db_config_properties(updated_at);
CREATE INDEX idx_db_config_properties_profile ON db_config_properties(profile);
```

### Semantics

- `profile IS NULL` means global/default value.
- `profile = 'prod'` (or any profile name) means profile-specific override.
- Refresh version is detected by `SELECT MAX(updated_at) FROM db_config_properties`.

## Basic Usage

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/app
    username: app
    password: app

dbconfig:
  refresh:
    enabled: true
    poll-interval: 10s
    initial-delay: 0s
```

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@RefreshScope
@Component
public class FeatureFlagService {

  @Value("${feature.x.enabled:false}")
  private boolean enabled;

  public boolean isEnabled() {
    return enabled;
  }
}
```

### Refresh behavior

1. DB snapshot is loaded into a dedicated `PropertySource`.
2. Trigger requests a refresh (polling, notify, or manual actuator call).
3. Snapshot is reloaded and applied.
4. `ContextRefresher.refresh()` runs.
5. `@RefreshScope` beans are re-instantiated with new values.

## Profile Resolution Rules

Resolution order:

1. Start with global rows (`profile IS NULL`).
2. Overlay active profiles in `spring.profiles.active` order.
3. Later profiles override earlier profiles for the same key.
4. If no active profiles exist, Spring default profiles are used.

### Example

Rows in `db_config_properties`:

| prop_key | profile | prop_value |
|---|---|---|
| `feature.x.enabled` | `NULL` | `false` |
| `feature.x.enabled` | `dev` | `true` |
| `feature.x.enabled` | `canary` | `false` |

If `spring.profiles.active=dev,canary`, the resolved value is `false` (last profile wins).

## Runtime Refresh Modes

### 1) Polling

The scheduler periodically checks DB version (`MAX(updated_at)`), reloads on change, and applies debounce/retry/fail-mode policies.

```yaml
dbconfig:
  refresh:
    polling:
      enabled: true
    poll-interval: 5s
    initial-delay: 0s
    refresh:
      coalesce-window: 1s
      min-interval: 5s
      max-wait: 30s
```

**Pros:** simple, DB-only dependency, predictable behavior.

**Cons:** periodic DB reads even when no config changed.

### 2) PostgreSQL `LISTEN/NOTIFY`

Use the optional notify starter:

```yaml
dbconfig:
  refresh:
    postgres-notify:
      enabled: true
      channel: dbconfig_refresh
      payload-format: NONE # NONE | JSON | TEXT_VERSION
      refresh-on-notify: true
      fallback-polling-enabled: true
      reconnect:
        initial-backoff: 500ms
        max-backoff: 30s
        jitter: 0.2
      dedupe:
        window: 1s
```

Send a notification manually:

```sql
NOTIFY dbconfig_refresh, '';
NOTIFY dbconfig_refresh, '{"version":1730500000000}';
```

Optional trigger function:

```sql
CREATE OR REPLACE FUNCTION notify_dbconfig_refresh()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  PERFORM pg_notify('dbconfig_refresh', '');
  RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS db_config_properties_notify_refresh ON db_config_properties;
CREATE TRIGGER db_config_properties_notify_refresh
AFTER INSERT OR UPDATE OR DELETE ON db_config_properties
FOR EACH STATEMENT EXECUTE FUNCTION notify_dbconfig_refresh();
```

### 3) Manual Actuator refresh

Trigger refresh manually:

```bash
curl -X POST http://localhost:8080/actuator/dbconfigrefresh
```

Typical JSON response:

```json
{
  "lastSuccessfulRefresh": "2026-02-24T14:03:21Z",
  "lastAttempt": "2026-02-24T14:05:00Z",
  "lastError": null,
  "snapshotKeys": 42,
  "degraded": false,
  "notifyEnabled": true,
  "notifyChannel": "dbconfig_refresh",
  "notifyListenerConnected": true,
  "lastNotifyAt": "2026-02-24T14:04:59Z",
  "refreshed": true,
  "changedKeysCount": 1,
  "newVersion": "2026-02-24T14:04:58Z",
  "durationMs": 37,
  "trigger": "MANUAL",
  "message": "Refresh completed"
}
```

## Configuration Properties

### Core refresh

| Property | Type | Default | Description |
|---|---|---|---|
| `dbconfig.refresh.enabled` | `boolean` | `true` | Master switch for the refresh subsystem. |
| `dbconfig.refresh.polling.enabled` | `boolean` | `true` | Enables polling scheduler. |
| `dbconfig.refresh.poll-interval` | `Duration` | `10s` | Polling interval for version checks. |
| `dbconfig.refresh.initial-delay` | `Duration` | `0s` | Delay before scheduler starts. |
| `dbconfig.refresh.property-source-name` | `String` | `dbConfig` | Name of the installed `PropertySource`. |
| `dbconfig.refresh.precedence.mode` | `FIRST\|LAST\|BEFORE\|AFTER` | `FIRST` | Placement strategy in environment property source order. |
| `dbconfig.refresh.precedence.relative-to` | `String` | `null` | Target source for `BEFORE/AFTER` modes. |
| `dbconfig.refresh.precedence.fail-if-relative-missing` | `boolean` | `false` | Fail startup if `relative-to` source is missing. |
| `dbconfig.refresh.refresh.coalesce-window` | `Duration` | `1s` | Debounce window for bursty trigger events. |
| `dbconfig.refresh.refresh.min-interval` | `Duration` | `5s` | Minimum interval between effective refresh executions. |
| `dbconfig.refresh.refresh.max-wait` | `Duration` | `30s` | Forces execution after continuous event stream. |

### Retry / backoff / failure mode

| Property | Type | Default | Description |
|---|---|---|---|
| `dbconfig.refresh.retry.max-attempts` | `int` | `5` | Retry attempts for DB operations. |
| `dbconfig.refresh.retry.initial-backoff` | `Duration` | `200ms` | Initial backoff delay. |
| `dbconfig.refresh.retry.max-backoff` | `Duration` | `5s` | Upper bound for backoff delay. |
| `dbconfig.refresh.retry.jitter` | `double` | `0.2` | Randomization factor (`0..1`). |
| `dbconfig.refresh.fail-soft` | `boolean` | `true` | Continue running when refresh fails. |
| `dbconfig.refresh.fail-soft.max-consecutive-failures` | `int` | `0` | If `>0`, marks degraded after threshold. |
| `dbconfig.refresh.fail-fast.mode` | `NONE\|STOP_SCHEDULER\|FAIL_APPLICATION` | `NONE` | Runtime behavior on refresh errors. |
| `dbconfig.refresh.fail-fast.on-initial-load` | `Boolean` | `null` | Explicit initial-load fail-fast; defaults to `!fail-soft` when unset. |

### Actuator

| Property | Type | Default | Description |
|---|---|---|---|
| `dbconfig.refresh.actuator.enabled` | `boolean` | `true` | Enables actuator integration. |
| `dbconfig.refresh.actuator.endpoint.enabled` | `boolean` | `true` | Enables `/actuator/dbconfigrefresh`. |
| `dbconfig.refresh.actuator.endpoint.id` | `String` | `dbconfigrefresh` | Endpoint id property (current endpoint path is `dbconfigrefresh`). |
| `dbconfig.refresh.actuator.health-enabled` | `boolean` | `true` | Enables `HealthIndicator`. |
| `dbconfig.refresh.actuator.info-enabled` | `boolean` | `true` | Enables `InfoContributor`. |
| `dbconfig.refresh.actuator.expose-details` | `boolean` | `false` | Adds optional details (active profiles, counters). |
| `dbconfig.refresh.actuator.health.stale-after` | `Duration` | `null` | Marks health `UNKNOWN` if last success is stale. |

### Metrics

| Property | Type | Default | Description |
|---|---|---|---|
| `dbconfig.refresh.metrics.enabled` | `boolean` | `true` | Enables refresh metrics when `MeterRegistry` exists. |
| `dbconfig.refresh.metrics.tags.profile` | `boolean` | `true` | Adds profile tag (`activeProfiles`) to refresh metrics. |

### PostgreSQL notify

| Property | Type | Default | Description |
|---|---|---|---|
| `dbconfig.refresh.postgres-notify.enabled` | `boolean` | `false` | Enables notify listener module. |
| `dbconfig.refresh.postgres-notify.channel` | `String` | `dbconfig_refresh` | LISTEN/NOTIFY channel name. |
| `dbconfig.refresh.postgres-notify.payload-format` | `NONE\|JSON\|TEXT_VERSION` | `NONE` | Payload parsing strategy. |
| `dbconfig.refresh.postgres-notify.refresh-on-notify` | `boolean` | `true` | Triggers refresh on incoming notify. |
| `dbconfig.refresh.postgres-notify.fallback-polling-enabled` | `boolean` | `true` | Keeps polling as fallback when notify is active. |
| `dbconfig.refresh.postgres-notify.reconnect.initial-backoff` | `Duration` | `500ms` | Initial reconnect delay. |
| `dbconfig.refresh.postgres-notify.reconnect.max-backoff` | `Duration` | `30s` | Maximum reconnect delay. |
| `dbconfig.refresh.postgres-notify.reconnect.jitter` | `double` | `0.2` | Reconnect jitter (`0..1`). |
| `dbconfig.refresh.postgres-notify.dedupe.window` | `Duration` | `1s` | Drops duplicate notify bursts inside window. |
| `dbconfig.refresh.postgres-notify.listen.connection.validation-interval` | `Duration` | `30s` | Listener connection validation cadence. |

## Actuator Integration

When Spring Boot Actuator is on the classpath and actuator integration is enabled, the project provides:

- `GET /actuator/dbconfigrefresh` — refresh subsystem state and metadata
- `POST /actuator/dbconfigrefresh` — manual refresh execution
- `GET /actuator/health` — includes `dbConfigRefresh` health contributor
- `GET /actuator/info` — includes `dbconfig` info section

Security is delegated to standard Actuator security configuration.

Property **values** are not exposed by these endpoints; only status/metadata is returned.

## Metrics

With Micrometer + `MeterRegistry` available, the following metrics are emitted.

Refresh metrics:

- Counters:
  - `dbconfig.refresh.poll.ticks`
  - `dbconfig.refresh.changes.detected`
  - `dbconfig.refresh.refresh.triggered`
  - `dbconfig.refresh.failures`
- Timers:
  - `dbconfig.refresh.db.lastUpdated.time`
  - `dbconfig.refresh.db.loadAll.time`
  - `dbconfig.refresh.contextRefresh.time`
- Gauges:
  - `dbconfig.refresh.last.success.epoch`
  - `dbconfig.refresh.consecutive.failures`
  - `dbconfig.refresh.degraded`

PostgreSQL notify metrics (when notify module + Micrometer are enabled):

- `dbconfig.refresh.notify.received`
- `dbconfig.refresh.notify.refresh.triggered`
- `dbconfig.refresh.notify.listener.reconnects`
- `dbconfig.refresh.notify.listener.connected`

Metrics are conditional; no-op implementations are used when metrics are disabled or absent.

## Production Considerations

### Fail-fast vs fail-soft

- **Fail-soft (`dbconfig.refresh.fail-soft=true`)** keeps application traffic alive during transient DB/config errors.
- **Fail-fast** is appropriate when stale configuration is unacceptable and startup/runtime failure is preferred over serving with old values.

### Avoiding refresh storms

Use:

- `refresh.coalesce-window` to group bursts,
- `refresh.min-interval` to cap trigger frequency,
- `refresh.max-wait` to ensure eventual refresh under constant events,
- notify dedupe window for PostgreSQL event bursts.

### Thread safety and bean lifecycle

- Refresh execution is synchronized in the refresh service.
- Beans requiring runtime value updates should be refresh-aware (`@RefreshScope`) or otherwise designed for dynamic reads.
- Stateful singleton beans that cache derived config require explicit invalidation strategy.

### Metrics cardinality

`metrics.tags.profile=true` adds profile-based tags; avoid high-cardinality dynamic tag values in production monitoring setups.

### Database load

- Polling frequency directly affects DB query volume.
- Keep index on `updated_at`.
- Prefer notify mode for low-latency, lower-idle-load environments.

## Extending the Project (SPI)

The design separates snapshot loading and triggering. You can extend it by adding custom provider/trigger implementations.

### Custom `ConfigSnapshotProvider`

A provider should return a version plus merged properties for active profiles.

```java
public interface ConfigSnapshotProvider {
  Instant currentVersion();
  Map<String, Object> loadMerged(List<String> activeProfiles);
}
```

Example adaptation idea:

```java
@Component
class MyConfigSnapshotProvider implements ConfigSnapshotProvider {
  @Override
  public Instant currentVersion() {
    // read from your storage version marker
    return Instant.now();
  }

  @Override
  public Map<String, Object> loadMerged(List<String> activeProfiles) {
    // load and merge config for profiles
    return Map.of("feature.x.enabled", "true");
  }
}
```

### Custom `RefreshTrigger`

A trigger should emit refresh requests based on an external event source:

```java
public interface RefreshTrigger {
  void start(Runnable onRefreshRequested);
  void stop();
}
```

Examples: Kafka topic trigger, Redis pub/sub trigger, HTTP callback trigger.

> In this repository, concrete trigger implementations are polling scheduler and PostgreSQL notify listener.

## Limitations

- A bootstrap datasource configuration is still required (the DB-backed config cannot configure its own initial DB connection).
- Runtime re-binding for field-injected `@Value` patterns requires Spring Cloud refresh mechanism.
- No distributed coordination barrier: each instance refreshes independently.
- No strict transactional consistency guarantee across all instances at exactly the same timestamp.
- Not a replacement for a full configuration platform in very large multi-service estates.

## Migration Guide

This repository currently exposes the modular starter layout directly:

- `db-config-refresh-spring-cloud-starter`
- `db-config-refresh-postgres-notify-starter`

If migrating from a single/legacy starter:

1. Replace old dependency with `db-config-refresh-spring-cloud-starter`.
2. Add `db-config-refresh-postgres-notify-starter` only if event-driven refresh is required.
3. Verify property namespaces under `dbconfig.refresh.*` (including nested `actuator.*` and `postgres-notify.*`).
4. Re-test actuator endpoints and refresh behavior in staging.

Compatibility policy recommendation:

- Keep existing `dbconfig.refresh.*` keys stable across minor releases.
- Document property renames with aliases for at least one minor release when possible.

## Contributing

- Fork the repository and open a pull request.
- Follow existing code style and package conventions.
- Add/adjust tests for behavioral changes.
- Integration tests rely on Testcontainers (Docker required locally/CI).

## License

MIT License
