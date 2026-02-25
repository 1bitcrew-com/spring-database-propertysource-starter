package com.example.dbconfig.refresh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

public class DbConfigPropertySourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(DbConfigPropertySourceInstaller.class);

    public DbConfigPropertySource installOrGet(ConfigurableEnvironment environment,
            String propertySourceName,
            DbConfigRefreshProperties.Precedence precedence) {
        MutablePropertySources sources = environment.getPropertySources();
        PropertySource<?> existing = sources.get(propertySourceName);
        if (existing instanceof DbConfigPropertySource dbConfigPropertySource) {
            return dbConfigPropertySource;
        }

        DbConfigPropertySource created = new DbConfigPropertySource(propertySourceName);
        placeSource(sources, created, precedence);
        return created;
    }

    private void placeSource(MutablePropertySources sources,
            DbConfigPropertySource propertySource,
            DbConfigRefreshProperties.Precedence precedence) {
        DbConfigRefreshProperties.Mode mode = precedence.getMode() == null
                ? DbConfigRefreshProperties.Mode.FIRST
                : precedence.getMode();

        switch (mode) {
            case FIRST -> sources.addFirst(propertySource);
            case LAST -> sources.addLast(propertySource);
            case BEFORE -> addRelative(sources, propertySource, precedence, true);
            case AFTER -> addRelative(sources, propertySource, precedence, false);
            default -> sources.addFirst(propertySource);
        }
    }

    private void addRelative(MutablePropertySources sources,
            DbConfigPropertySource propertySource,
            DbConfigRefreshProperties.Precedence precedence,
            boolean before) {
        String relativeTo = precedence.getRelativeTo();
        if (!StringUtils.hasText(relativeTo)) {
            throw new IllegalArgumentException("dbconfig.refresh.precedence.relative-to must be set for mode "
                    + precedence.getMode());
        }

        if (sources.contains(relativeTo)) {
            if (before) {
                sources.addBefore(relativeTo, propertySource);
            }
            else {
                sources.addAfter(relativeTo, propertySource);
            }
            return;
        }

        if (precedence.isFailIfRelativeMissing()) {
            throw new IllegalStateException("PropertySource '" + relativeTo + "' not found for precedence mode "
                    + precedence.getMode());
        }

        log.warn("PropertySource '{}' not found for precedence mode {}. Falling back to LAST.", relativeTo,
                precedence.getMode());
        sources.addLast(propertySource);
    }
}
