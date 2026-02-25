package com.example.dbconfig.refresh.postgres;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import com.example.dbconfig.refresh.ConfigRefreshOrchestrator;
import com.example.dbconfig.core.TriggerReason;
import com.example.dbconfig.refresh.DbConfigRefreshState;



public class PostgresNotifyListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PostgresNotifyListener.class);

    private final DataSource dataSource;
    private final ConfigRefreshOrchestrator orchestrator;
    private final DbConfigRefreshState state;
    private final PostgresNotifyRefreshProperties properties;
    private final PostgresNotifyPayloadInterpreter payloadInterpreter;
    private final PostgresNotifyMetrics metrics;
    private final Clock clock;

    private volatile boolean running;
    private volatile Thread worker;
    private volatile Connection listenConnection;
    private volatile Instant nextRefreshNotBefore = Instant.EPOCH;

    public PostgresNotifyListener(DataSource dataSource,
            ConfigRefreshOrchestrator orchestrator,
            DbConfigRefreshState state,
            PostgresNotifyRefreshProperties properties,
            PostgresNotifyPayloadInterpreter payloadInterpreter,
            PostgresNotifyMetrics metrics) {
        this(dataSource, orchestrator, state, properties, payloadInterpreter, metrics, Clock.systemUTC());
    }

    PostgresNotifyListener(DataSource dataSource,
            ConfigRefreshOrchestrator orchestrator,
            DbConfigRefreshState state,
            PostgresNotifyRefreshProperties properties,
            PostgresNotifyPayloadInterpreter payloadInterpreter,
            PostgresNotifyMetrics metrics,
            Clock clock) {
        this.dataSource = dataSource;
        this.orchestrator = orchestrator;
        this.state = state;
        this.properties = properties;
        this.payloadInterpreter = payloadInterpreter;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        state.setNotifyEnabled(true);
        state.setNotifyChannel(properties.getChannel());
        running = true;
        Thread thread = new Thread(this::runLoop, "dbconfig-pg-notify-listener");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    @Override
    public synchronized void stop() {
        running = false;
        state.setNotifyEnabled(false);
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
        }
        closeConnectionQuietly();
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 10;
    }


    public void forceReconnectForTest() {
        markDisconnected();
    }

    private void runLoop() {
        Duration backoff = properties.getReconnect().getInitialBackoff();
        while (running) {
            try {
                openAndListen();
                backoff = properties.getReconnect().getInitialBackoff();
                loopNotifications();
            }
            catch (Exception ex) {
                if (!running) {
                    break;
                }
                markDisconnected();
                metrics.incrementReconnects();
                sleepWithBackoff(backoff);
                backoff = nextBackoff(backoff);
            }
        }
        markDisconnected();
    }

    private void openAndListen() throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(true);
        try (Statement statement = connection.createStatement()) {
            statement.execute("LISTEN " + properties.getChannel());
        }
        listenConnection = connection;
        state.setListenConnected(true);
        metrics.recordListenerConnected(true);
        log.info("Postgres LISTEN active on channel={}", properties.getChannel());
    }

    private void loopNotifications() throws SQLException, InterruptedException {
        Instant lastValidation = clock.instant();
        Duration validationInterval = properties.getListen().getConnection().getValidationInterval();

        while (running) {
            PGConnection pgConnection = listenConnection.unwrap(PGConnection.class);
            PGNotification[] notifications = pgConnection.getNotifications();
            if (notifications != null) {
                for (PGNotification notification : notifications) {
                    onNotification(notification.getParameter());
                }
            }

            Instant now = clock.instant();
            if (Duration.between(lastValidation, now).compareTo(validationInterval) >= 0) {
                validateConnection();
                lastValidation = now;
            }

            Thread.sleep(200);
        }
    }

    private void onNotification(String payload) {
        metrics.incrementNotifyReceived();
        state.setLastNotifyAt(clock.instant());
        if (!properties.isRefreshOnNotify()) {
            return;
        }

        PostgresNotifyPayloadInterpreter.NotifyDecision decision = payloadInterpreter.evaluate(payload, state.getLastDbVersionSeen());
        if (!decision.shouldRefresh()) {
            return;
        }

        Instant now = clock.instant();
        if (now.isBefore(nextRefreshNotBefore)) {
            return;
        }
        nextRefreshNotBefore = now.plus(properties.getDedupe().getWindow());

        orchestrator.requestRefresh(TriggerReason.EVENT);
        metrics.incrementNotifyRefreshTriggered();
        if (true) {
            Instant payloadVersion = decision.payloadVersion();
            if (payloadVersion != null) {
                state.setLastNotifyAt(now);
            }
        }
    }

    private void validateConnection() throws SQLException {
        try (Statement statement = listenConnection.createStatement()) {
            statement.execute("SELECT 1");
        }
    }

    private void markDisconnected() {
        state.setListenConnected(false);
        metrics.recordListenerConnected(false);
        closeConnectionQuietly();
    }

    private void closeConnectionQuietly() {
        Connection connection = listenConnection;
        listenConnection = null;
        if (connection == null) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("UNLISTEN *");
        }
        catch (SQLException ignored) {
        }
        try {
            connection.close();
        }
        catch (SQLException ignored) {
        }
    }

    private Duration nextBackoff(Duration current) {
        long capped = Math.min(current.toMillis() * 2, properties.getReconnect().getMaxBackoff().toMillis());
        double jitter = Math.max(0.0, properties.getReconnect().getJitter());
        double random = 1.0 + ((ThreadLocalRandom.current().nextDouble() * 2) - 1) * jitter;
        long withJitter = Math.max(50L, Math.round(capped * random));
        return Duration.ofMillis(withJitter);
    }

    private void sleepWithBackoff(Duration waitTime) {
        try {
            Thread.sleep(Math.max(50L, waitTime.toMillis()));
        }
        catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
