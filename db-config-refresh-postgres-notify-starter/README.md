# db-config-refresh-postgres-notify-starter

Opcjonalny moduł dla `db-config-refresh-spring-cloud-starter` dodający event-driven refresh przez Postgres `LISTEN/NOTIFY`.

## Konfiguracja

```yaml
dbconfig:
  refresh:
    polling:
      enabled: true
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
      listen:
        connection:
          validation-interval: 30s
```

> Gdy `fallback-polling-enabled=false`, listener wyłącza polling (`dbconfig.refresh.polling.enabled=false`).

## Emisja eventów

Manualnie:

```sql
NOTIFY dbconfig_refresh, '';
NOTIFY dbconfig_refresh, '{"version":1730500000000}';
```

Przykładowy trigger:

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
