INSERT INTO db_config_properties(prop_key, profile, prop_value, updated_at)
VALUES ('my.dynamic.value', NULL, 'one', now())
ON CONFLICT (prop_key, profile)
DO UPDATE SET prop_value = EXCLUDED.prop_value, updated_at = EXCLUDED.updated_at;

INSERT INTO db_config_properties(prop_key, profile, prop_value, updated_at)
VALUES ('my.dynamic.value', 'dev', 'dev-one', now())
ON CONFLICT (prop_key, profile)
DO UPDATE SET prop_value = EXCLUDED.prop_value, updated_at = EXCLUDED.updated_at;

INSERT INTO db_config_properties(prop_key, profile, prop_value, updated_at)
VALUES ('demo.threshold', NULL, '5', now())
ON CONFLICT (prop_key, profile)
DO UPDATE SET prop_value = EXCLUDED.prop_value, updated_at = EXCLUDED.updated_at;

INSERT INTO db_config_properties(prop_key, profile, prop_value, updated_at)
VALUES ('demo.mode', NULL, 'alpha', now())
ON CONFLICT (prop_key, profile)
DO UPDATE SET prop_value = EXCLUDED.prop_value, updated_at = EXCLUDED.updated_at;
