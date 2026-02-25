package com.example.dbconfig.testapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.dbconfig.refresh.DbConfigRefreshScheduler;
import com.example.dbconfig.refresh.postgres.PostgresNotifyListener;

@Testcontainers
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        classes = TestApplication.class,
        properties = {
                "spring.profiles.active=dev",
                "dbconfig.refresh.polling.enabled=false",
                "dbconfig.refresh.poll-interval=5m",
                "dbconfig.refresh.initial-delay=0ms",
                "dbconfig.refresh.retry.max-attempts=1",
                "dbconfig.refresh.postgres-notify.enabled=true",
                "dbconfig.refresh.postgres-notify.channel=dbconfig_refresh",
                "dbconfig.refresh.postgres-notify.fallback-polling-enabled=false",
                "dbconfig.refresh.postgres-notify.payload-format=NONE",
                "dbconfig.refresh.postgres-notify.listen.connection.validation-interval=1s",
                "management.endpoints.web.exposure.include=*",
                "spring.sql.init.mode=always"
        })
class PostgresNotifyRefreshIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private DynamicValueBean dynamicValueBean;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PostgresNotifyListener notifyListener;

    @Test
    void notifyShouldTriggerRefreshWithoutPollingScheduler() {
        assertThat(context.getBeansOfType(DbConfigRefreshScheduler.class)).isEmpty();
        assertThat(dynamicValueBean.getVal()).isEqualTo("dev-one");

        jdbcTemplate.update(
                "UPDATE db_config_properties SET prop_value = ?, updated_at = now() WHERE prop_key = ? AND profile = ?",
                "dev-two",
                "my.dynamic.value",
                "dev");
        jdbcTemplate.execute("NOTIFY dbconfig_refresh, ''");

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(dynamicValueBean.getVal()).isEqualTo("dev-two"));

        Map<?, ?> endpoint = restTemplate.getForObject("/actuator/dbconfigrefresh", Map.class);
        assertThat(endpoint).isNotNull();
        assertThat(endpoint.get("notifyEnabled")).isEqualTo(true);
        assertThat(endpoint.get("notifyListenerConnected")).isEqualTo(true);
        assertThat(endpoint.get("notifyChannel")).isEqualTo("dbconfig_refresh");
    }

    @Test
    void listenerShouldReconnectAfterDatabaseRestart() {
        assertThat(dynamicValueBean.getVal()).isEqualTo("dev-one");

        notifyListener.forceReconnectForTest();

        jdbcTemplate.update(
                "UPDATE db_config_properties SET prop_value = ?, updated_at = now() WHERE prop_key = ? AND profile = ?",
                "dev-three",
                "my.dynamic.value",
                "dev");
        jdbcTemplate.execute("NOTIFY dbconfig_refresh, ''");

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> assertThat(dynamicValueBean.getVal()).isEqualTo("dev-three"));

        Map<?, ?> endpoint = restTemplate.getForObject("/actuator/dbconfigrefresh", Map.class);
        assertThat(endpoint).isNotNull();
        assertThat(endpoint.get("notifyListenerConnected")).isEqualTo(true);
    }
}
