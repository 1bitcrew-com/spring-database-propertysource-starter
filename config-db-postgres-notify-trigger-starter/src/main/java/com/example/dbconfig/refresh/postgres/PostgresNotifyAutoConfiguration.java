package com.example.dbconfig.refresh.postgres;

import javax.sql.DataSource;

import io.micrometer.core.instrument.MeterRegistry;
import org.postgresql.PGConnection;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.dbconfig.refresh.DbConfigRefreshProperties;
import com.example.dbconfig.refresh.ConfigRefreshOrchestrator;
import com.example.dbconfig.refresh.DbConfigRefreshState;

@AutoConfiguration
@ConditionalOnClass(PGConnection.class)
@ConditionalOnBean({ DataSource.class, ConfigRefreshOrchestrator.class })
@ConditionalOnProperty(prefix = "dbconfig.refresh.postgres-notify", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(PostgresNotifyRefreshProperties.class)
public class PostgresNotifyAutoConfiguration {

    @Bean
    @ConditionalOnMissingClass("com.fasterxml.jackson.databind.ObjectMapper")
    @ConditionalOnMissingBean
    PostgresNotifyPayloadInterpreter postgresNotifyPayloadInterpreter(PostgresNotifyRefreshProperties properties) {
        return new PostgresNotifyPayloadInterpreter(properties, null);
    }

    @Bean
    @ConditionalOnMissingBean
    PostgresNotifyMetrics postgresNotifyMetrics() {
        return new NoopPostgresNotifyMetrics();
    }

    @Bean
    @ConditionalOnMissingBean
    PostgresNotifyListener postgresNotifyListener(DataSource dataSource,
            ConfigRefreshOrchestrator orchestrator,
            DbConfigRefreshState state,
            DbConfigRefreshProperties refreshProperties,
            PostgresNotifyRefreshProperties properties,
            PostgresNotifyPayloadInterpreter payloadInterpreter,
            PostgresNotifyMetrics metrics) {
        if (!properties.isFallbackPollingEnabled()) {
            refreshProperties.setPollingEnabled(false);
        }
        return new PostgresNotifyListener(dataSource, orchestrator, state, properties, payloadInterpreter, metrics);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class MicrometerNotifyMetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean(PostgresNotifyMetrics.class)
        PostgresNotifyMetrics micrometerPostgresNotifyMetrics(MeterRegistry meterRegistry) {
            return new MicrometerPostgresNotifyMetrics(meterRegistry);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
    static class JacksonPayloadConfiguration {

        @Bean
        @ConditionalOnMissingBean(PostgresNotifyPayloadInterpreter.class)
        PostgresNotifyPayloadInterpreter jacksonPostgresNotifyPayloadInterpreter(PostgresNotifyRefreshProperties properties,
                com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
            return new PostgresNotifyPayloadInterpreter(properties, objectMapper);
        }
    }
}
