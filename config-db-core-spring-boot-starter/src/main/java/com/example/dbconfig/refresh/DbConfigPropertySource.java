package com.example.dbconfig.refresh;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.core.env.EnumerablePropertySource;

public class DbConfigPropertySource extends EnumerablePropertySource<Object> {

    private final AtomicReference<Map<String, Object>> snapshot = new AtomicReference<>(Collections.emptyMap());

    public DbConfigPropertySource(String name) {
        super(name, new Object());
    }

    public void reload(Map<String, Object> properties) {
        snapshot.set(Map.copyOf(properties));
    }

    @Override
    public Object getProperty(String name) {
        return snapshot.get().get(name);
    }

    @Override
    public String[] getPropertyNames() {
        return snapshot.get().keySet().toArray(String[]::new);
    }

    public int size() {
        return snapshot.get().size();
    }
}
