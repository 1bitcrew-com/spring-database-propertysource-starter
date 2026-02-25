package com.example.dbconfig.refresh;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@EnableConfigurationProperties(DbConfigRefreshProperties.class)
@ConditionalOnClass(ContextRefresher.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "dbconfig.refresh", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DbConfigRefreshAutoConfiguration {

    @org.springframework.context.annotation.Bean
    @ConditionalOnMissingBean
    DbConfigJdbcRepository dbConfigJdbcRepository(JdbcTemplate jdbcTemplate) {
        return new DbConfigJdbcRepository(jdbcTemplate);
    }

    @org.springframework.context.annotation.Bean
    @ConditionalOnMissingBean
    DbConfigPropertySourceInstaller dbConfigPropertySourceInstaller() {
        return new DbConfigPropertySourceInstaller();
    }

    @org.springframework.context.annotation.Bean
    @ConditionalOnMissingBean
    DbConfigPropertySource dbConfigPropertySource(ConfigurableEnvironment environment,
            DbConfigPropertySourceInstaller installer,
            DbConfigRefreshProperties properties) {
        return installer.installOrGet(environment, properties.getPropertySourceName(), properties.getPrecedence());
    }

    @org.springframework.context.annotation.Bean
    @ConditionalOnMissingBean
    DbConfigRefreshScheduler dbConfigRefreshScheduler(DbConfigJdbcRepository repository,
            DbConfigPropertySource propertySource,
            ContextRefresher contextRefresher,
            DbConfigRefreshProperties properties,
            ConfigurableEnvironment environment) {
        return new DbConfigRefreshScheduler(repository, propertySource, contextRefresher, properties, environment);
    }
}
