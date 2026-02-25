package com.example.dbconfig.core;

public interface RefreshTrigger {
    void start();
    void stop();
    boolean isRunning();
}
