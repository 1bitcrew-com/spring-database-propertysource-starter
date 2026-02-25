package com.example.dbconfig.refresh.postgres;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class PostgresNotifyPayloadInterpreter {

    private final PostgresNotifyRefreshProperties properties;
    private final ObjectMapper objectMapper;

    PostgresNotifyPayloadInterpreter(PostgresNotifyRefreshProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    NotifyDecision evaluate(String payload, Instant lastSeenVersion) {
        if (payload == null || payload.isBlank()) {
            return NotifyDecision.refresh(null);
        }

        return switch (properties.getPayloadFormat()) {
            case NONE -> NotifyDecision.refresh(null);
            case TEXT_VERSION -> evaluateVersion(payload.trim(), lastSeenVersion);
            case JSON -> evaluateJson(payload, lastSeenVersion);
        };
    }

    private NotifyDecision evaluateJson(String payload, Instant lastSeenVersion) {
        if (objectMapper == null) {
            return NotifyDecision.refresh(null);
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            Instant payloadVersion = null;
            JsonNode versionNode = root.path("version");
            if (!versionNode.isMissingNode() && !versionNode.isNull()) {
                payloadVersion = parseInstant(versionNode.asText());
            }
            JsonNode profilesNode = root.path("profiles");
            if (profilesNode.isArray()) {
                Iterator<JsonNode> ignored = profilesNode.elements();
                while (ignored.hasNext()) {
                    ignored.next();
                }
            }
            return shouldRefreshForVersion(payloadVersion, lastSeenVersion);
        }
        catch (Exception ignored) {
            return NotifyDecision.refresh(null);
        }
    }

    private NotifyDecision evaluateVersion(String payload, Instant lastSeenVersion) {
        return shouldRefreshForVersion(parseInstant(payload), lastSeenVersion);
    }

    private NotifyDecision shouldRefreshForVersion(Instant payloadVersion, Instant lastSeenVersion) {
        if (payloadVersion == null) {
            return NotifyDecision.refresh(null);
        }
        if (lastSeenVersion != null && !payloadVersion.isAfter(lastSeenVersion)) {
            return NotifyDecision.ignore(payloadVersion);
        }
        return NotifyDecision.refresh(payloadVersion);
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(raw));
        }
        catch (NumberFormatException ignored) {
            try {
                return Instant.parse(raw);
            }
            catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

    record NotifyDecision(boolean shouldRefresh, Instant payloadVersion) {

        static NotifyDecision refresh(Instant payloadVersion) {
            return new NotifyDecision(true, payloadVersion);
        }

        static NotifyDecision ignore(Instant payloadVersion) {
            return new NotifyDecision(false, payloadVersion);
        }
    }
}
