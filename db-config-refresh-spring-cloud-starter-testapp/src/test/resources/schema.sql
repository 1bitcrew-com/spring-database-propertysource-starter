CREATE TABLE IF NOT EXISTS db_config_properties (
    prop_key VARCHAR(255) PRIMARY KEY,
    prop_value TEXT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
