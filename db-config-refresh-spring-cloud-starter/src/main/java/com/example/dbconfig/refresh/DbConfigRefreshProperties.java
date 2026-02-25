package com.example.dbconfig.refresh;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dbconfig.refresh")
public class DbConfigRefreshProperties {

    private boolean enabled = true;

    private Duration pollInterval = Duration.ofSeconds(10);

    private boolean failSoft = true;

    private String propertySourceName = "dbConfig";

    private final Precedence precedence = new Precedence();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public boolean isFailSoft() {
        return failSoft;
    }

    public void setFailSoft(boolean failSoft) {
        this.failSoft = failSoft;
    }

    public String getPropertySourceName() {
        return propertySourceName;
    }

    public void setPropertySourceName(String propertySourceName) {
        this.propertySourceName = propertySourceName;
    }

    public Precedence getPrecedence() {
        return precedence;
    }

    public static class Precedence {

        private Mode mode = Mode.FIRST;

        private String relativeTo;

        private boolean failIfRelativeMissing = false;

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public String getRelativeTo() {
            return relativeTo;
        }

        public void setRelativeTo(String relativeTo) {
            this.relativeTo = relativeTo;
        }

        public boolean isFailIfRelativeMissing() {
            return failIfRelativeMissing;
        }

        public void setFailIfRelativeMissing(boolean failIfRelativeMissing) {
            this.failIfRelativeMissing = failIfRelativeMissing;
        }
    }

    public enum Mode {
        FIRST,
        LAST,
        BEFORE,
        AFTER
    }
}
