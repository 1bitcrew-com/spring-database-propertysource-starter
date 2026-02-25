package com.example.dbconfig.refresh;

import com.example.dbconfig.core.ConfigSnapshotProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

@AutoConfiguration
@EnableConfigurationProperties(DbConfigRefreshProperties.class)
@ConditionalOnProperty(prefix = "dbconfig.refresh", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DbConfigCoreAutoConfiguration {

    @Bean @ConditionalOnMissingBean
    DbConfigPropertySourceInstaller dbConfigPropertySourceInstaller() { return new DbConfigPropertySourceInstaller(); }

    @Bean @ConditionalOnMissingBean
    DbConfigPropertySource dbConfigPropertySource(ConfigurableEnvironment environment, DbConfigPropertySourceInstaller installer, DbConfigRefreshProperties properties) {
        return installer.installOrGet(environment, properties.getPropertySourceName(), properties.getPrecedence());
    }

    @Bean @ConditionalOnMissingBean
    DbConfigRefreshState dbConfigRefreshState() { return new DbConfigRefreshRuntimeState(); }

    @Bean @ConditionalOnMissingBean(DbConfigRefreshMetrics.class)
    DbConfigRefreshMetrics dbConfigRefreshMetrics() { return new NoopDbConfigRefreshMetrics(); }

    @Bean @ConditionalOnBean(ConfigSnapshotProvider.class) @ConditionalOnMissingBean
    ConfigRefreshOrchestrator configRefreshOrchestrator(ConfigSnapshotProvider provider, DbConfigPropertySource ps, DbConfigRefreshProperties p, ConfigurableEnvironment env, DbConfigRefreshState s, DbConfigRefreshMetrics m, ApplicationEventPublisher publisher) {
        return new ConfigRefreshOrchestrator(provider, ps, p, env, s, m, publisher);
    }

    @Bean @ConditionalOnBean(ConfigRefreshOrchestrator.class) @ConditionalOnProperty(prefix="dbconfig.refresh.polling",name="enabled",havingValue="true",matchIfMissing=true)
    PollingRefreshTrigger pollingRefreshTrigger(ConfigRefreshOrchestrator orchestrator, DbConfigRefreshProperties properties) { return new PollingRefreshTrigger(orchestrator, properties); }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "dbconfig.refresh.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class MetricsConfiguration {
        @Bean @ConditionalOnMissingBean(DbConfigRefreshMetrics.class)
        DbConfigRefreshMetrics micrometerDbConfigRefreshMetrics(MeterRegistry meterRegistry, DbConfigRefreshState state, DbConfigRefreshProperties properties, ConfigurableEnvironment environment) {
            String[] activeProfiles = environment.getActiveProfiles();
            String profileTagValue = activeProfiles.length == 0 ? "default" : String.join(",", activeProfiles);
            return new MicrometerDbConfigRefreshMetrics(meterRegistry, state, properties.getPropertySourceName(), profileTagValue, "SOFT", properties.getMetrics().getTags().isProfile());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Endpoint.class)
    @ConditionalOnBean(ConfigRefreshOrchestrator.class)
    @ConditionalOnProperty(prefix = "dbconfig.refresh.actuator", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class ActuatorConfiguration {
        @Bean @ConditionalOnMissingBean @ConditionalOnProperty(prefix = "dbconfig.refresh.actuator.endpoint", name = "enabled", havingValue = "true", matchIfMissing = true)
        DbConfigRefreshEndpoint dbConfigRefreshEndpoint(ConfigRefreshOrchestrator orchestrator, DbConfigRefreshState state, DbConfigRefreshProperties properties) { return new DbConfigRefreshEndpoint(orchestrator, state, properties); }
        @Bean @ConditionalOnClass(InfoContributor.class) @ConditionalOnMissingBean
        DbConfigRefreshInfoContributor dbConfigRefreshInfoContributor(DbConfigRefreshState state, DbConfigRefreshProperties properties) { return new DbConfigRefreshInfoContributor(state, properties); }
        @Bean("dbConfigRefreshHealthIndicator") @ConditionalOnClass(HealthIndicator.class) @ConditionalOnEnabledHealthIndicator("dbConfigRefresh")
        DbConfigRefreshHealthIndicator dbConfigRefreshHealthIndicator(DbConfigRefreshState state, DbConfigRefreshProperties properties) { return new DbConfigRefreshHealthIndicator(state, properties); }
    }
}
