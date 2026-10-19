package me.cxdev.sapbtp.testing.monitoring;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import me.cxdev.sapbtp.testing.auth.OAuth2TokenProvider;
import me.cxdev.sapbtp.testing.config.CpiTestConfig;
import me.cxdev.sapbtp.testing.http.HttpResponse;
import me.cxdev.sapbtp.testing.http.HttpTransport;
import okhttp3.Credentials;

public class CpiMonitoringClient {
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final HttpTransport transport;
    private final CpiTestConfig config;
    private final MonitoringParser parser;
    private final Sleeper sleeper;
    private final OAuth2TokenProvider tokenProvider;

    public CpiMonitoringClient(HttpTransport transport, CpiTestConfig config) {
        this(transport, config, new MonitoringParser(), Thread::sleep);
    }

    public CpiMonitoringClient(HttpTransport transport, CpiTestConfig config, MonitoringParser parser, Sleeper sleeper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.config = Objects.requireNonNull(config, "config");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.tokenProvider = config.isMonitoringOAuth2Configured()
                ? new OAuth2TokenProvider(
                        transport, config.getTokenUrl(), config.getMonitoringClientId(), config.getMonitoringClientSecret())
                : null;
    }

    public List<String> fetchMessageIds(String correlationId, String testRunId) {
        long deadline = System.currentTimeMillis() + config.getPollingTimeoutMs();
        while (true) {
            MonitoringResponse parsedResponse = loadByCorrelationId(correlationId, testRunId);
            List<String> messageIds = parsedResponse.messageIds();
            if (!messageIds.isEmpty()) {
                return messageIds;
            }
            if (System.currentTimeMillis() >= deadline) {
                return List.of();
            }
            sleepQuietly(correlationId, testRunId);
        }
    }

    public String getCorrelationIdForMessageId(String messageId, String testRunId) {
        Objects.requireNonNull(messageId, "messageId");
        long deadline = System.currentTimeMillis() + config.getPollingTimeoutMs();
        while (true) {
            MonitoringResponse response = loadByMessageId(messageId, testRunId);
            String correlationId = response.correlationIdForMessageId(messageId);
            if (correlationId != null && !correlationId.isBlank()) {
                return correlationId;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new IllegalStateException("No correlationId found for messageId=" + messageId + ", testRunId=" + testRunId);
            }
            sleepQuietly("messageId=" + messageId, testRunId);
        }
    }

    public List<MonitoringEntry> awaitCompletion(String correlationId, String testRunId) {
        Objects.requireNonNull(correlationId, "correlationId");
        long deadline = System.currentTimeMillis() + config.getPollingTimeoutMs();
        MonitoringResponse latestResponse = new MonitoringResponse(List.of());
        while (true) {
            latestResponse = loadByCorrelationId(correlationId, testRunId);
            if (latestResponse.allEntriesTerminal()) {
                return latestResponse.entries();
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new IllegalStateException("Monitoring polling timed out before terminal status for correlationId="
                        + correlationId + ", testRunId=" + testRunId + ", statuses=" + latestResponse.entries().stream()
                        .map(entry -> entry.messageId() + ":" + entry.status())
                        .toList());
            }
            sleepQuietly(correlationId, testRunId);
        }
    }

    private MonitoringResponse loadByCorrelationId(String correlationId, String testRunId) {
        HttpResponse response = transport.get(buildCorrelationUrl(correlationId), buildHeaders());
        return parseResponse(response, "correlationId=" + correlationId, testRunId);
    }

    private MonitoringResponse loadByMessageId(String messageId, String testRunId) {
        HttpResponse response = transport.get(buildMessageIdUrl(messageId), buildHeaders());
        return parseResponse(response, "messageId=" + messageId, testRunId);
    }

    private MonitoringResponse parseResponse(HttpResponse response, String identifier, String testRunId) {
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Monitoring API returned status " + response.statusCode() + " for " + identifier + ", testRunId=" + testRunId);
        }
        return parser.parse(response.body());
    }

    private String buildCorrelationUrl(String correlationId) {
        String baseUrl = config.getMonitoringBaseUrl().endsWith("/")
                ? config.getMonitoringBaseUrl().substring(0, config.getMonitoringBaseUrl().length() - 1)
                : config.getMonitoringBaseUrl();
        String filter = URLEncoder.encode("CorrelationId eq '" + correlationId + "'", StandardCharsets.UTF_8);
        return baseUrl + "/api/v1/MessageProcessingLogs?$filter=" + filter + "&$format=json";
    }

    private String buildMessageIdUrl(String messageId) {
        String baseUrl = config.getMonitoringBaseUrl().endsWith("/")
                ? config.getMonitoringBaseUrl().substring(0, config.getMonitoringBaseUrl().length() - 1)
                : config.getMonitoringBaseUrl();
        String filter = URLEncoder.encode("MessageGuid eq '" + messageId + "'", StandardCharsets.UTF_8);
        return baseUrl + "/api/v1/MessageProcessingLogs?$filter=" + filter + "&$format=json";
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", tokenProvider != null
                ? "Bearer " + tokenProvider.getAccessToken()
                : Credentials.basic(config.getMonitoringUser(), config.getMonitoringPassword()));
        headers.put("Accept", "application/json");
        return headers;
    }

    private void sleepQuietly(String correlationId, String testRunId) {
        try {
            sleeper.sleep(config.getPollingIntervalMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Monitoring polling interrupted for correlationId=" + correlationId + ", testRunId=" + testRunId,
                    e);
        }
    }
}
