package com.example.dbconfig.refresh;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

public class DbConfigJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public DbConfigJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Instant getLastUpdated() {
        Timestamp ts = jdbcTemplate.queryForObject("SELECT MAX(updated_at) FROM db_config_properties", Timestamp.class);
        return ts == null ? Instant.EPOCH : ts.toInstant();
    }

    public Map<String, Object> loadAll() {
        return jdbcTemplate.query("SELECT prop_key, prop_value FROM db_config_properties", rs -> {
            Map<String, Object> result = new LinkedHashMap<>();
            while (rs.next()) {
                result.put(rs.getString("prop_key"), rs.getString("prop_value"));
            }
            return result;
        });
    }
}
