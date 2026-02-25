package com.example.dbconfig.testapp;

import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Component
@RefreshScope
public class DemoPropsHolder {

    private final DemoProps demoProps;

    public DemoPropsHolder(DemoProps demoProps) {
        this.demoProps = demoProps;
    }

    public int getThreshold() {
        return demoProps.getThreshold();
    }

    public String getMode() {
        return demoProps.getMode();
    }
}
