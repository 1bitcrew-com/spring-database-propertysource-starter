INSERT INTO db_config_properties(prop_key, prop_value, updated_at)
VALUES ('my.dynamic.value', 'one', now())
ON CONFLICT (prop_key)
DO UPDATE SET prop_value = EXCLUDED.prop_value, updated_at = EXCLUDED.updated_at;
