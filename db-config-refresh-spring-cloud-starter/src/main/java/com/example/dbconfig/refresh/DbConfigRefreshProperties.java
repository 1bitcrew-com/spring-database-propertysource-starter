package com.example.dbconfig.refresh;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dbconfig.refresh")
public class DbConfigRefreshProperties {

    private boolean enabled = true;

    private Duration pollInterval = Duration.ofSeconds(10);

    private Duration initialDelay = Duration.ZERO;

    private boolean failSoft = true;

    private String propertySourceName = "dbConfig";

    private final Precedence precedence = new Precedence();

    private final Refresh refresh = new Refresh();

    private final Retry retry = new Retry();

    private final FailSoft failSoftSettings = new FailSoft();

    private final FailFast failFast = new FailFast();

    private final Metrics metrics = new Metrics();

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

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(Duration initialDelay) {
        this.initialDelay = initialDelay;
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

    public Refresh getRefresh() {
        return refresh;
    }

    public Retry getRetry() {
        return retry;
    }

    public FailSoft getFailSoftSettings() {
        return failSoftSettings;
    }

    public FailFast getFailFast() {
        return failFast;
    }

    public Metrics getMetrics() {
        return metrics;
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

    public static class Refresh {

        private Duration minInterval = Duration.ofSeconds(5);

        private Duration maxWait = Duration.ofSeconds(30);

        private Duration coalesceWindow = Duration.ofSeconds(1);

        public Duration getMinInterval() {
            return minInterval;
        }

        public void setMinInterval(Duration minInterval) {
            this.minInterval = minInterval;
        }

        public Duration getMaxWait() {
            return maxWait;
        }

        public void setMaxWait(Duration maxWait) {
            this.maxWait = maxWait;
        }

        public Duration getCoalesceWindow() {
            return coalesceWindow;
        }

        public void setCoalesceWindow(Duration coalesceWindow) {
            this.coalesceWindow = coalesceWindow;
        }
    }

    public static class Retry {

        private int maxAttempts = 5;

        private Duration initialBackoff = Duration.ofMillis(200);

        private Duration maxBackoff = Duration.ofSeconds(5);

        private double jitter = 0.2;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }

        public double getJitter() {
            return jitter;
        }

        public void setJitter(double jitter) {
            this.jitter = jitter;
        }
    }

    public static class FailSoft {

        private int maxConsecutiveFailures = 0;

        public int getMaxConsecutiveFailures() {
            return maxConsecutiveFailures;
        }

        public void setMaxConsecutiveFailures(int maxConsecutiveFailures) {
            this.maxConsecutiveFailures = maxConsecutiveFailures;
        }
    }

    public static class FailFast {

        private Mode mode = Mode.NONE;

        private Boolean onInitialLoad;

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public Boolean getOnInitialLoad() {
            return onInitialLoad;
        }

        public void setOnInitialLoad(Boolean onInitialLoad) {
            this.onInitialLoad = onInitialLoad;
        }

        public enum Mode {
            NONE,
            STOP_SCHEDULER,
            FAIL_APPLICATION
        }
    }

    public static class Metrics {

        private boolean enabled = true;

        private final Tags tags = new Tags();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Tags getTags() {
            return tags;
        }

        public static class Tags {

            private boolean profile = true;

            public boolean isProfile() {
                return profile;
            }

            public void setProfile(boolean profile) {
                this.profile = profile;
            }
        }
    }

    public enum Mode {
        FIRST,
        LAST,
        BEFORE,
        AFTER
    }
}
