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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.dbconfig.refresh.DbConfigJdbcRepository;
import com.example.dbconfig.refresh.DbConfigRefreshRuntimeState;

@Testcontainers
@SpringBootTest(
        classes = { TestApplication.class, DbConfigRefreshIntegrationTest.TestBeans.class },
        properties = {
                "spring.profiles.active=dev",
                "dbconfig.refresh.poll-interval=200ms",
                "dbconfig.refresh.initial-delay=0ms",
                "dbconfig.refresh.refresh.min-interval=2s",
                "dbconfig.refresh.refresh.max-wait=3s",
                "dbconfig.refresh.refresh.coalesce-window=400ms",
                "dbconfig.refresh.retry.max-attempts=1",
                "dbconfig.refresh.fail-soft=true",
                "dbconfig.refresh.fail-soft.max-consecutive-failures=2",
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
    private DemoPropsHolder demoPropsHolder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DbConfigRefreshRuntimeState runtimeState;

    @Autowired
    private FailureSwitch failureSwitch;

    @Test
    void shouldRefreshRefreshScopeValueUsingActiveProfileOverride() {
        assertThat(dynamicValueBean.getVal()).isEqualTo("dev-one");

        jdbcTemplate.update(
                "UPDATE db_config_properties SET prop_value = ?, updated_at = now() WHERE prop_key = ? AND profile = ?",
                "dev-two",
                "my.dynamic.value",
                "dev");

        Awaitility.await()
                .atMost(Duration.ofSeconds(12))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(dynamicValueBean.getVal()).isEqualTo("dev-two"));
    }

    @Test
    void shouldRebindConfigurationPropertiesAfterRefresh() {
        assertThat(demoPropsHolder.getThreshold()).isEqualTo(5);
        assertThat(demoPropsHolder.getMode()).isEqualTo("alpha");

        jdbcTemplate.update(
                "UPDATE db_config_properties SET prop_value = ?, updated_at = now() WHERE prop_key = ? AND profile IS NULL",
                "7",
                "demo.threshold");
        jdbcTemplate.update(
                "UPDATE db_config_properties SET prop_value = ?, updated_at = now() WHERE prop_key = ? AND profile IS NULL",
                "beta",
                "demo.mode");

        Awaitility.await()
                .atMost(Duration.ofSeconds(12))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    assertThat(demoPropsHolder.getThreshold()).isEqualTo(7);
                    assertThat(demoPropsHolder.getMode()).isEqualTo("beta");
                });
    }

    @Test
    void shouldCoalesceBurstOfUpdatesAndApplyFinalValue() throws InterruptedException {
        long refreshBaseline = runtimeState.getRefreshTriggeredCount();

        for (int i = 1; i <= 5; i++) {
            jdbcTemplate.update(
                    "UPDATE db_config_properties SET prop_value = ?, updated_at = now() WHERE prop_key = ? AND profile = ?",
                    "debounced-" + i,
                    "my.dynamic.value",
                    "dev");
            Thread.sleep(100L);
        }

        Awaitility.await()
                .atMost(Duration.ofSeconds(12))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(dynamicValueBean.getVal()).isEqualTo("debounced-5"));

        long refreshDelta = runtimeState.getRefreshTriggeredCount() - refreshBaseline;
        assertThat(refreshDelta).isLessThanOrEqualTo(2L);
    }

    @Test
    void shouldKeepLastSnapshotWhenDbFailsInFailSoftMode() {
        jdbcTemplate.update(
                "UPDATE db_config_properties SET prop_value = ?, updated_at = now() WHERE prop_key = ? AND profile = ?",
                "one",
                "my.dynamic.value",
                "dev");

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(dynamicValueBean.getVal()).isEqualTo("one"));

        failureSwitch.setFail(true);

        jdbcTemplate.update(
                "UPDATE db_config_properties SET prop_value = ?, updated_at = now() WHERE prop_key = ? AND profile = ?",
                "two",
                "my.dynamic.value",
                "dev");

        Awaitility.await()
                .during(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(4))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(dynamicValueBean.getVal()).isEqualTo("one"));

        failureSwitch.setFail(false);

        Awaitility.await()
                .atMost(Duration.ofSeconds(12))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(dynamicValueBean.getVal()).isEqualTo("two"));
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
