package com.example.dbconfig.refresh.postgres;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dbconfig.refresh.postgres-notify")
public class PostgresNotifyRefreshProperties {

    private boolean enabled = false;
    private String channel = "dbconfig_refresh";
    private PayloadFormat payloadFormat = PayloadFormat.NONE;
    private boolean refreshOnNotify = true;
    private boolean fallbackPollingEnabled = true;
    private final Reconnect reconnect = new Reconnect();
    private final Dedupe dedupe = new Dedupe();
    private final Listen listen = new Listen();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public PayloadFormat getPayloadFormat() {
        return payloadFormat;
    }

    public void setPayloadFormat(PayloadFormat payloadFormat) {
        this.payloadFormat = payloadFormat;
    }

    public boolean isRefreshOnNotify() {
        return refreshOnNotify;
    }

    public void setRefreshOnNotify(boolean refreshOnNotify) {
        this.refreshOnNotify = refreshOnNotify;
    }

    public boolean isFallbackPollingEnabled() {
        return fallbackPollingEnabled;
    }

    public void setFallbackPollingEnabled(boolean fallbackPollingEnabled) {
        this.fallbackPollingEnabled = fallbackPollingEnabled;
    }

    public Reconnect getReconnect() {
        return reconnect;
    }

    public Dedupe getDedupe() {
        return dedupe;
    }

    public Listen getListen() {
        return listen;
    }

    public enum PayloadFormat {
        NONE,
        JSON,
        TEXT_VERSION
    }

    public static class Reconnect {
        private Duration initialBackoff = Duration.ofMillis(500);
        private Duration maxBackoff = Duration.ofSeconds(30);
        private double jitter = 0.2;

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

    public static class Dedupe {
        private Duration window = Duration.ofSeconds(1);

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }

    public static class Listen {
        private final Connection connection = new Connection();

        public Connection getConnection() {
            return connection;
        }

        public static class Connection {
            private Duration validationInterval = Duration.ofSeconds(30);

            public Duration getValidationInterval() {
                return validationInterval;
            }

            public void setValidationInterval(Duration validationInterval) {
                this.validationInterval = validationInterval;
            }
        }
    }
}
