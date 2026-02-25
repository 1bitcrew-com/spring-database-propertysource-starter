package com.example.dbconfig.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.env.MapPropertySource;

class DbConfigPropertySourceInstallerTest {

    private final DbConfigPropertySourceInstaller installer = new DbConfigPropertySourceInstaller();

    @Test
    void shouldInstallAfterRelativePropertySource() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource("systemEnvironment", java.util.Map.of()));

        DbConfigRefreshProperties.Precedence precedence = new DbConfigRefreshProperties.Precedence();
        precedence.setMode(DbConfigRefreshProperties.Mode.AFTER);
        precedence.setRelativeTo("systemEnvironment");

        installer.installOrGet(environment, "dbConfig", precedence);

        assertThat(environment.getPropertySources().precedenceOf(environment.getPropertySources().get("dbConfig")))
                .isGreaterThan(environment.getPropertySources().precedenceOf(environment.getPropertySources().get("systemEnvironment")));
    }

    @Test
    void shouldFallbackToLastWhenRelativeMissingAndFailFastDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource("baseline", java.util.Map.of()));

        DbConfigRefreshProperties.Precedence precedence = new DbConfigRefreshProperties.Precedence();
        precedence.setMode(DbConfigRefreshProperties.Mode.BEFORE);
        precedence.setRelativeTo("missing");
        precedence.setFailIfRelativeMissing(false);

        installer.installOrGet(environment, "dbConfig", precedence);

        assertThat(environment.getPropertySources().precedenceOf(environment.getPropertySources().get("dbConfig")))
                .isEqualTo(environment.getPropertySources().size() - 1);
    }

    @Test
    void shouldFailWhenRelativeMissingAndFailFastEnabled() {
        MockEnvironment environment = new MockEnvironment();

        DbConfigRefreshProperties.Precedence precedence = new DbConfigRefreshProperties.Precedence();
        precedence.setMode(DbConfigRefreshProperties.Mode.AFTER);
        precedence.setRelativeTo("missing");
        precedence.setFailIfRelativeMissing(true);

        assertThatThrownBy(() -> installer.installOrGet(environment, "dbConfig", precedence))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }
}
