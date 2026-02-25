package com.example.dbconfig.testapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        classes = TestApplication.class,
        properties = {
                "spring.profiles.active=dev",
                "dbconfig.refresh.poll-interval=1s",
                "dbconfig.refresh.fail-soft=true",
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

    @Test
    void shouldRefreshRefreshScopeValueUsingActiveProfileOverride() {
        assertThat(dynamicValueBean.getVal()).isEqualTo("dev-one");

        jdbcTemplate.update(
                "UPDATE db_config_properties SET prop_value = ?, updated_at = now() WHERE prop_key = ? AND profile = ?",
                "dev-two",
                "my.dynamic.value",
                "dev");

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
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
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    assertThat(demoPropsHolder.getThreshold()).isEqualTo(7);
                    assertThat(demoPropsHolder.getMode()).isEqualTo("beta");
                });
    }
}
