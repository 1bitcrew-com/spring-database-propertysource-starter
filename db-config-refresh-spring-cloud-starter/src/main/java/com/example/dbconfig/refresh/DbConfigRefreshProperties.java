package com.example.dbconfig.refresh;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dbconfig.refresh")
public class DbConfigRefreshProperties {

    private boolean enabled = true;

    private Duration pollInterval = Duration.ofSeconds(10);

    private boolean failSoft = true;

    private String propertySourceName = "dbConfig";

    private int order = 0;

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

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
