package com.example.dbconfig.refresh;

import javax.sql.DataSource;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@EnableConfigurationProperties(DbConfigRefreshProperties.class)
@ConditionalOnClass(ContextRefresher.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "dbconfig.refresh", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DbConfigRefreshAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DbConfigJdbcRepository dbConfigJdbcRepository(JdbcTemplate jdbcTemplate) {
        return new DbConfigJdbcRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    DbConfigPropertySourceInstaller dbConfigPropertySourceInstaller() {
        return new DbConfigPropertySourceInstaller();
    }

    @Bean
    @ConditionalOnMissingBean
    DbConfigPropertySource dbConfigPropertySource(ConfigurableEnvironment environment,
            DbConfigPropertySourceInstaller installer,
            DbConfigRefreshProperties properties) {
        return installer.installOrGet(environment, properties.getPropertySourceName(), properties.getPrecedence());
    }

    @Bean
    @ConditionalOnMissingBean
    DbConfigRefreshRuntimeState dbConfigRefreshRuntimeState() {
        return new DbConfigRefreshRuntimeState();
    }

    @Bean
    @ConditionalOnMissingBean(DbConfigRefreshMetrics.class)
    DbConfigRefreshMetrics dbConfigRefreshMetrics() {
        return new NoopDbConfigRefreshMetrics();
    }

    @Bean
    @ConditionalOnMissingBean
    DbConfigRefreshScheduler dbConfigRefreshScheduler(DbConfigJdbcRepository repository,
            DbConfigPropertySource propertySource,
            ContextRefresher contextRefresher,
            DbConfigRefreshProperties properties,
            ConfigurableEnvironment environment,
            DbConfigRefreshRuntimeState runtimeState,
            DbConfigRefreshMetrics metrics) {
        return new DbConfigRefreshScheduler(repository,
                propertySource,
                contextRefresher,
                properties,
                environment,
                runtimeState,
                metrics);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "dbconfig.refresh.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean(DbConfigRefreshMetrics.class)
        DbConfigRefreshMetrics micrometerDbConfigRefreshMetrics(MeterRegistry meterRegistry,
                DbConfigRefreshRuntimeState runtimeState,
                DbConfigRefreshProperties properties,
                ConfigurableEnvironment environment) {
            String[] activeProfiles = environment.getActiveProfiles();
            String profileTagValue = activeProfiles.length == 0 ? "default" : String.join(",", activeProfiles);
            String failMode = properties.getFailFast().getMode() == DbConfigRefreshProperties.FailFast.Mode.NONE
                    ? "SOFT"
                    : "FAST";
            return new MicrometerDbConfigRefreshMetrics(
                    meterRegistry,
                    runtimeState,
                    properties.getPropertySourceName(),
                    profileTagValue,
                    failMode,
                    properties.getMetrics().getTags().isProfile());
        }
    }
}
