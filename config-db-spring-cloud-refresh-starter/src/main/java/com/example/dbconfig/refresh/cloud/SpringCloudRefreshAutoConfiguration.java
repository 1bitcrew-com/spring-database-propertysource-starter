package com.example.dbconfig.refresh.cloud;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.cloud.context.refresh.ContextRefresher;

@AutoConfiguration
@ConditionalOnClass(ContextRefresher.class)
public class SpringCloudRefreshAutoConfiguration {
    @Bean
    @ConditionalOnBean(ContextRefresher.class)
    SpringCloudRefreshListener springCloudRefreshListener(ContextRefresher refresher) {
        return new SpringCloudRefreshListener(refresher);
    }
}
