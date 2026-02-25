package com.example.dbconfig.refresh.jdbc;

import javax.sql.DataSource;

import com.example.dbconfig.core.ConfigSnapshotProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@ConditionalOnBean(DataSource.class)
@EnableConfigurationProperties(JdbcConfigSourceProperties.class)
public class JdbcConfigSourceAutoConfiguration {
    @Bean @ConditionalOnMissingBean
    DbConfigJdbcRepository dbConfigJdbcRepository(JdbcTemplate jdbcTemplate) { return new DbConfigJdbcRepository(jdbcTemplate); }
    @Bean @ConditionalOnMissingBean(ConfigSnapshotProvider.class)
    JdbcConfigSnapshotProvider jdbcConfigSnapshotProvider(DbConfigJdbcRepository repository) { return new JdbcConfigSnapshotProvider(repository); }
}
