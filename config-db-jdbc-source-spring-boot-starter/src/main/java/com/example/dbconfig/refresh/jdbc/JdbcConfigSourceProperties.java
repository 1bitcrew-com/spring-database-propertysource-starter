package com.example.dbconfig.refresh.jdbc;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dbconfig.source.jdbc")
public class JdbcConfigSourceProperties {
    private String table = "db_config_properties";
    private Columns column = new Columns();
    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }
    public Columns getColumn() { return column; }
    public static class Columns {
        private String key = "property_key";
        private String profile = "profile";
        private String value = "property_value";
        private String updatedAt = "updated_at";
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getProfile() { return profile; }
        public void setProfile(String profile) { this.profile = profile; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    }
}
