# db-config-refresh-spring-cloud-starter

Starter ładuje konfigurację z tabeli `db_config_properties` do `Environment` jako dynamiczny `PropertySource`, okresowo wykrywa zmiany i wywołuje `ContextRefresher.refresh()`.

## Schema (Iteration 2)

```sql
CREATE TABLE db_config_properties (
  prop_key VARCHAR(255) NOT NULL,
  profile VARCHAR(100) NULL,
  prop_value TEXT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT now(),
  PRIMARY KEY (prop_key, profile)
);

CREATE INDEX idx_db_config_properties_updated_at ON db_config_properties(updated_at);
CREATE INDEX idx_db_config_properties_profile ON db_config_properties(profile);
```

## Profile rules

- rekordy `profile IS NULL` są bazą (global/default),
- potem profile aktywne z `Environment#getActiveProfiles()` są nakładane w kolejności zwróconej przez Spring,
- jeśli brak aktywnych profili, używany jest fallback do `Environment#getDefaultProfiles()`,
- późniejszy profil nadpisuje wcześniejszy.

## PropertySource precedence

Konfiguracja pod prefixem `dbconfig.refresh.precedence`:

- `mode`: `FIRST`, `LAST`, `BEFORE`, `AFTER`,
- `relative-to`: wymagane dla `BEFORE/AFTER`,
- `fail-if-relative-missing` (default `false`):
  - `true` → fail-fast,
  - `false` → fallback do `LAST` + warning log.

## Production readiness (Iteration 3)

### Polling i lifecycle

- `dbconfig.refresh.poll-interval` – interwał pollingu.
- `dbconfig.refresh.initial-delay` – opóźnienie startu schedulera.
- Scheduler używa pojedynczego wątku `dbconfig-refresh-*` (daemon) i zatrzymuje się razem z kontekstem (`SmartLifecycle`).

### Debounce / throttle

- `dbconfig.refresh.refresh.coalesce-window` (domyślnie `1s`) – zbiera serię zmian w jeden refresh.
- `dbconfig.refresh.refresh.min-interval` (domyślnie `5s`) – minimalny odstęp między realnymi refreshami.
- `dbconfig.refresh.refresh.max-wait` (domyślnie `30s`) – wymusza refresh, nawet przy ciągłym strumieniu zmian.

### Retry/backoff/jitter (DB)

- `dbconfig.refresh.retry.max-attempts` (domyślnie `5`)
- `dbconfig.refresh.retry.initial-backoff` (domyślnie `200ms`)
- `dbconfig.refresh.retry.max-backoff` (domyślnie `5s`)
- `dbconfig.refresh.retry.jitter` (domyślnie `0.2`, zakres `0..1`)

Retry dotyczy operacji:

- `getLastUpdated()`
- `loadMergedForProfiles(...)`

### Fail-soft / fail-fast

- `dbconfig.refresh.fail-soft` – tryb łagodny (nie zrywa działania aplikacji przy błędach runtime DB).
- `dbconfig.refresh.fail-soft.max-consecutive-failures` (`0` = bez limitu) – po przekroczeniu przejście w `degraded` i ograniczenie log spam.
- `dbconfig.refresh.fail-fast.mode`:
  - `NONE` – standardowe fail-soft,
  - `STOP_SCHEDULER` – zatrzymuje polling przy błędzie runtime,
  - `FAIL_APPLICATION` – propaguje wyjątek runtime i może zakończyć aplikację.
- `dbconfig.refresh.fail-fast.on-initial-load` – zachowanie przy błędzie initial load. Gdy nieustawione, domyślnie `true` jeśli `fail-soft=false`, inaczej `false`.

### Metryki Micrometer (opcjonalne)

Metryki są aktywne tylko gdy `MeterRegistry` jest na classpath i `dbconfig.refresh.metrics.enabled=true`.

Konfigurowalne tagi:

- `dbconfig.refresh.metrics.tags.profile` – dodaje tag aktywnych profili (`activeProfiles`), domyślnie `true`.

Rejestrowane metryki:

- Counters: `dbconfig.refresh.poll.ticks`, `dbconfig.refresh.changes.detected`, `dbconfig.refresh.refresh.triggered`, `dbconfig.refresh.failures`
- Timers: `dbconfig.refresh.db.lastUpdated.time`, `dbconfig.refresh.db.loadAll.time`, `dbconfig.refresh.contextRefresh.time`
- Gauges: `dbconfig.refresh.last.success.epoch`, `dbconfig.refresh.consecutive.failures`, `dbconfig.refresh.degraded`

### Zalecane wartości startowe

- `poll-interval`: `1s-5s`
- `coalesce-window`: `500ms-2s`
- `min-interval`: `3s-10s`
- `max-wait`: `20s-60s`
- `retry.max-attempts`: `3-5`
- `retry.initial-backoff`: `100ms-500ms`
- `retry.max-backoff`: `2s-10s`


## Actuator integration (Iteration 4)

Wsparcie jest warunkowe (`@ConditionalOnClass`) i aktywuje się tylko gdy Actuator jest na classpath.

Nowe właściwości (`dbconfig.refresh.actuator.*`):

- `enabled` (default `true`)
- `endpoint.enabled` (default `true`)
- `endpoint.id` (default `dbconfigrefresh`) – **TODO**: w tej iteracji endpoint ma stałe id `dbconfigrefresh`.
- `health-enabled` (default `true`)
- `info-enabled` (default `true`)
- `expose-details` (default `false`)
- `health.stale-after` (optional) – po przekroczeniu czasu od ostatniego sukcesu status `UNKNOWN`.

Dostarczane elementy:

- `/actuator/dbconfigrefresh`:
  - `GET` (`@ReadOperation`) status/metadane refresh,
  - `POST` (`@WriteOperation`) manualny refresh z tą samą logiką co scheduler.
- `InfoContributor` (`/actuator/info`, sekcja `dbconfig`) – wersja, ostatnie czasy, liczba kluczy, status degraded.
- `HealthIndicator` (`dbConfigRefresh`) – status `UP` / `OUT_OF_SERVICE` / `DEGRADED` / `DOWN`.

Bezpieczeństwo:

- endpointy korzystają ze standardowych mechanizmów security Actuator,
- brak ekspozycji wartości konfiguracji (tylko metadane).

## Limitations

- polling (brak LISTEN/NOTIFY),
- brak namespace/tenant/label.
