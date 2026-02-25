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

## Limitations

- polling (brak LISTEN/NOTIFY),
- brak namespace/tenant/label,
- brak actuator endpointów.
