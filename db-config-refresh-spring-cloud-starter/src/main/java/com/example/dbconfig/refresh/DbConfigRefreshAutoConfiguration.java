package com.example.dbconfig.refresh;

import javax.sql.DataSource;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.health.HealthIndicator;
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
    DbConfigRefreshState dbConfigRefreshState() {
        return new DbConfigRefreshRuntimeState();
    }

    @Bean
    @ConditionalOnMissingBean(DbConfigRefreshMetrics.class)
    DbConfigRefreshMetrics dbConfigRefreshMetrics() {
        return new NoopDbConfigRefreshMetrics();
    }

    @Bean
    @ConditionalOnMissingBean
    DbConfigRefreshService dbConfigRefreshService(DbConfigJdbcRepository repository,
            DbConfigPropertySource propertySource,
            ContextRefresher contextRefresher,
            DbConfigRefreshProperties properties,
            ConfigurableEnvironment environment,
            DbConfigRefreshState state,
            DbConfigRefreshMetrics metrics) {
        return new DbConfigRefreshService(repository,
                propertySource,
                contextRefresher,
                properties,
                environment,
                state,
                metrics);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "dbconfig.refresh.polling", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "dbconfig.refresh.postgres-notify", name = "fallback-polling-enabled", havingValue = "true", matchIfMissing = true)
    DbConfigRefreshScheduler dbConfigRefreshScheduler(DbConfigRefreshService refreshService,
            DbConfigRefreshProperties properties,
            DbConfigRefreshState state,
            DbConfigRefreshMetrics metrics) {
        return new DbConfigRefreshScheduler(refreshService, properties, state, metrics);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "dbconfig.refresh.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean(DbConfigRefreshMetrics.class)
        DbConfigRefreshMetrics micrometerDbConfigRefreshMetrics(MeterRegistry meterRegistry,
                DbConfigRefreshState state,
                DbConfigRefreshProperties properties,
                ConfigurableEnvironment environment) {
            String[] activeProfiles = environment.getActiveProfiles();
            String profileTagValue = activeProfiles.length == 0 ? "default" : String.join(",", activeProfiles);
            String failMode = properties.getFailFast().getMode() == DbConfigRefreshProperties.FailFast.Mode.NONE
                    ? "SOFT"
                    : "FAST";
            return new MicrometerDbConfigRefreshMetrics(
                    meterRegistry,
                    state,
                    properties.getPropertySourceName(),
                    profileTagValue,
                    failMode,
                    properties.getMetrics().getTags().isProfile());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Endpoint.class)
    @ConditionalOnProperty(prefix = "dbconfig.refresh.actuator", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class ActuatorConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "dbconfig.refresh.actuator.endpoint", name = "enabled", havingValue = "true", matchIfMissing = true)
        DbConfigRefreshEndpoint dbConfigRefreshEndpoint(DbConfigRefreshService refreshService,
                DbConfigRefreshState state,
                DbConfigRefreshProperties properties) {
            return new DbConfigRefreshEndpoint(refreshService, state, properties);
        }

        @Bean
        @ConditionalOnClass(InfoContributor.class)
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "dbconfig.refresh.actuator", name = "info-enabled", havingValue = "true", matchIfMissing = true)
        DbConfigRefreshInfoContributor dbConfigRefreshInfoContributor(DbConfigRefreshState state,
                DbConfigRefreshProperties properties) {
            return new DbConfigRefreshInfoContributor(state, properties);
        }

        @Bean("dbConfigRefreshHealthIndicator")
        @ConditionalOnClass(HealthIndicator.class)
        @ConditionalOnEnabledHealthIndicator("dbConfigRefresh")
        @ConditionalOnProperty(prefix = "dbconfig.refresh.actuator", name = "health-enabled", havingValue = "true", matchIfMissing = true)
        DbConfigRefreshHealthIndicator dbConfigRefreshHealthIndicator(DbConfigRefreshState state,
                DbConfigRefreshProperties properties) {
            return new DbConfigRefreshHealthIndicator(state, properties);
        }
    }
}
