CREATE TABLE IF NOT EXISTS db_config_properties (
    prop_key VARCHAR(255) NOT NULL,
    profile VARCHAR(100) NULL,
    prop_value TEXT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (prop_key, profile)
);

CREATE INDEX IF NOT EXISTS idx_db_config_properties_updated_at ON db_config_properties (updated_at);
CREATE INDEX IF NOT EXISTS idx_db_config_properties_profile ON db_config_properties (profile);
