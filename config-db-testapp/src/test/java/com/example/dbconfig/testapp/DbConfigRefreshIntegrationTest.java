package com.example.dbconfig.testapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.TestConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.dbconfig.refresh.DbConfigJdbcRepository;
import com.example.dbconfig.refresh.DbConfigRefreshState;

@Testcontainers
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        classes = { TestApplication.class, DbConfigRefreshIntegrationTest.TestBeans.class },
        properties = {
                "spring.profiles.active=dev",
                "dbconfig.refresh.poll-interval=5m",
                "dbconfig.refresh.initial-delay=0ms",
                "dbconfig.refresh.retry.max-attempts=1",
                "dbconfig.refresh.fail-soft=true",
                "dbconfig.refresh.fail-soft.max-consecutive-failures=1",
                "dbconfig.refresh.actuator.enabled=true",
                "dbconfig.refresh.actuator.endpoint.enabled=true",
                "dbconfig.refresh.actuator.info-enabled=true",
                "dbconfig.refresh.actuator.health-enabled=true",
                "dbconfig.refresh.actuator.expose-details=true",
                "management.endpoints.web.exposure.include=*",
                "management.endpoint.health.show-details=always",
                "spring.sql.init.mode=always"
        })
class DbConfigRefreshIntegrationTest {

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
    private TestRestTemplate restTemplate;

    @Autowired
    private DbConfigRefreshState refreshState;

    @Autowired
    private FailureSwitch failureSwitch;

    @Test
    void manualRefreshEndpointShouldReloadRefreshScopeValue() {
        assertThat(dynamicValueBean.getVal()).isEqualTo("dev-one");

        jdbcTemplate.update(
                "UPDATE db_config_properties SET prop_value = ?, updated_at = now() WHERE prop_key = ? AND profile = ?",
                "dev-two",
                "my.dynamic.value",
                "dev");

        ResponseEntity<Map> response = restTemplate.postForEntity("/actuator/dbconfigrefresh", null, Map.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("refreshed")).isEqualTo(true);
        assertThat(response.getBody()).containsKeys("newVersion", "durationMs", "snapshotKeys");

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(dynamicValueBean.getVal()).isEqualTo("dev-two"));
    }

    @Test
    void infoEndpointShouldContainDbConfigMetadataWithoutValues() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/actuator/info", Map.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();

        Object dbConfigObject = response.getBody().get("dbconfig");
        assertThat(dbConfigObject).isInstanceOf(Map.class);

        Map<?, ?> dbConfig = (Map<?, ?>) dbConfigObject;
        assertThat(dbConfig).containsKeys("version", "lastSuccess", "keysCount");
        assertThat(dbConfig).doesNotContainKey("values");
    }

    @Test
    void healthEndpointShouldSwitchFromUpToDegradedAndBackToUp() {
        ResponseEntity<Map> initialHealth = restTemplate.getForEntity("/actuator/health/dbConfigRefresh", Map.class);
        assertThat(initialHealth.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(initialHealth.getBody()).isNotNull();
        assertThat(initialHealth.getBody().get("status")).isEqualTo("UP");

        failureSwitch.setFail(true);
        ResponseEntity<Map> failedRefresh = restTemplate.postForEntity("/actuator/dbconfigrefresh", null, Map.class);
        assertThat(failedRefresh.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(failedRefresh.getBody()).isNotNull();
        assertThat(failedRefresh.getBody().get("refreshed")).isEqualTo(false);

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    ResponseEntity<Map> degradedHealth = restTemplate.getForEntity("/actuator/health/dbConfigRefresh", Map.class);
                    assertThat(degradedHealth.getStatusCode().is2xxSuccessful()).isTrue();
                    assertThat(degradedHealth.getBody()).isNotNull();
                    assertThat(degradedHealth.getBody().get("status")).isNotEqualTo("UP");
                });

        failureSwitch.setFail(false);
        ResponseEntity<Map> recoveredRefresh = restTemplate.postForEntity("/actuator/dbconfigrefresh", null, Map.class);
        assertThat(recoveredRefresh.getStatusCode().is2xxSuccessful()).isTrue();

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    ResponseEntity<Map> recoveredHealth = restTemplate.getForEntity("/actuator/health/dbConfigRefresh", Map.class);
                    assertThat(recoveredHealth.getStatusCode().is2xxSuccessful()).isTrue();
                    assertThat(recoveredHealth.getBody()).isNotNull();
                    assertThat(recoveredHealth.getBody().get("status")).isEqualTo("UP");
                    assertThat(refreshState.getConsecutiveFailures()).isZero();
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        FailureSwitch failureSwitch() {
            return new FailureSwitch();
        }

        @Bean
        @Primary
        DbConfigJdbcRepository testRepository(JdbcTemplate jdbcTemplate, FailureSwitch failureSwitch) {
            return new ToggleableDbConfigJdbcRepository(jdbcTemplate, failureSwitch);
        }
    }

    static class FailureSwitch {

        private final AtomicBoolean fail = new AtomicBoolean(false);

        void setFail(boolean shouldFail) {
            fail.set(shouldFail);
        }

        boolean shouldFail() {
            return fail.get();
        }
    }

    static class ToggleableDbConfigJdbcRepository extends DbConfigJdbcRepository {

        private final FailureSwitch failureSwitch;

        ToggleableDbConfigJdbcRepository(JdbcTemplate jdbcTemplate, FailureSwitch failureSwitch) {
            super(jdbcTemplate);
            this.failureSwitch = failureSwitch;
        }

        @Override
        public Instant getLastUpdated() {
            maybeFail();
            return super.getLastUpdated();
        }

        @Override
        public Map<String, Object> loadMergedForProfiles(List<String> profiles) {
            maybeFail();
            return super.loadMergedForProfiles(profiles);
        }

        private void maybeFail() {
            if (failureSwitch.shouldFail()) {
                throw new IllegalStateException("Simulated database outage");
            }
        }
    }
}
