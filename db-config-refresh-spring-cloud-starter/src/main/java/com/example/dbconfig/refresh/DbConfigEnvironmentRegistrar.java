package com.example.dbconfig.refresh;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

public class DbConfigEnvironmentRegistrar {

    public DbConfigPropertySource registerOrGet(ConfigurableEnvironment environment, String propertySourceName, int order) {
        MutablePropertySources sources = environment.getPropertySources();
        PropertySource<?> existing = sources.get(propertySourceName);
        if (existing instanceof DbConfigPropertySource dbConfigPropertySource) {
            return dbConfigPropertySource;
        }

        DbConfigPropertySource created = new DbConfigPropertySource(propertySourceName);
        // TODO: honor order to place DB source below sysprops/envvars while still being high precedence.
        sources.addFirst(created);
        return created;
    }
}
