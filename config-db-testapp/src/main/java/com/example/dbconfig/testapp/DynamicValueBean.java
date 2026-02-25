package com.example.dbconfig.testapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Component
@RefreshScope
public class DynamicValueBean {

    @Value("${my.dynamic.value:default}")
    private String val;

    public String getVal() {
        return val;
    }
}
