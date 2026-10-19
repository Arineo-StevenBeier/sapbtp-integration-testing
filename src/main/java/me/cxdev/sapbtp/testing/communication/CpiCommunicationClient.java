package me.cxdev.sapbtp.testing.communication;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import me.cxdev.sapbtp.testing.auth.OAuth2TokenProvider;
import me.cxdev.sapbtp.testing.config.CpiTestConfig;
import me.cxdev.sapbtp.testing.http.HttpResponse;
import me.cxdev.sapbtp.testing.http.HttpTransport;
import me.cxdev.sapbtp.testing.monitoring.CpiMonitoringClient;
import me.cxdev.sapbtp.testing.monitoring.MonitoringEntry;
import okhttp3.Credentials;
import org.json.JSONObject;

public class CpiCommunicationClient {
    private static final List<String> MESSAGE_ID_KEYS = List.of(
            "SAP_MessageProcessingLogID",
            "SAP-MessageProcessingLogID",
            "MessageGuid",
            "MessageId",
            "messageId",
            "messageGuid");
    private static final List<String> CORRELATION_ID_KEYS = List.of(
            "CorrelationId",
            "CorrelationID",
            "correlationId",
            "X-Correlation-Id");
    private static final List<String> TEST_RUN_ID_KEYS = List.of("x-test-run-id", "testRunId");

    private final HttpTransport transport;
    private final CpiTestConfig config;
    private final CpiMonitoringClient monitoringClient;
    private final ClassLoader classLoader;
    private final OAuth2TokenProvider tokenProvider;

    public CpiCommunicationClient(HttpTransport transport, CpiTestConfig config, CpiMonitoringClient monitoringClient) {
        this(transport, config, monitoringClient, Thread.currentThread().getContextClassLoader());
    }

    CpiCommunicationClient(
            HttpTransport transport,
            CpiTestConfig config,
            CpiMonitoringClient monitoringClient,
            ClassLoader classLoader) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.config = Objects.requireNonNull(config, "config");
        this.monitoringClient = Objects.requireNonNull(monitoringClient, "monitoringClient");
        this.classLoader = classLoader;
        this.tokenProvider = config.isOAuth2Configured()
                ? new OAuth2TokenProvider(transport, config.getTokenUrl(), config.getClientId(), config.getClientSecret())
                : null;
    }

    public CpiSendResult sendRawRequest(String resourcePath, String payload) {
        return sendRawRequest(resourcePath, payload, detectContentType(resourcePath, payload));
    }

    public CpiSendResult sendRawRequest(String resourcePath, String payload, String contentType) {
        HttpResponse response = transport.post(buildUrl(resourcePath), payload, buildHeaders(), contentType);
        return toSendResult(response);
    }

    public CpiSendResult sendFileRequest(String resourcePath, String localPath) {
        String payload = loadPayload(localPath);
        return sendRawRequest(resourcePath, payload, detectContentType(localPath, payload));
    }

    public String getCorrelationIdForMessageId(String messageId, String testRunId) {
        return monitoringClient.getCorrelationIdForMessageId(messageId, testRunId);
    }

    public List<MonitoringEntry> waitForCompletion(String correlationId, String testRunId) {
        return monitoringClient.awaitCompletion(correlationId, testRunId);
    }

    public List<MonitoringEntry> waitForCompletionForMessageId(String messageId, String testRunId) {
        String correlationId = getCorrelationIdForMessageId(messageId, testRunId);
        return waitForCompletion(correlationId, testRunId);
    }

    private CpiSendResult toSendResult(HttpResponse response) {
        String messageId = firstMatchingValue(response.headers(), MESSAGE_ID_KEYS);
        String correlationId = firstMatchingValue(response.headers(), CORRELATION_ID_KEYS);
        String testRunId = firstMatchingValue(response.headers(), TEST_RUN_ID_KEYS);
        if ((messageId == null || messageId.isBlank()) || (correlationId == null || correlationId.isBlank())) {
            Map<String, String> responseBodyValues = parseBodyFields(response.body());
            if (messageId == null || messageId.isBlank()) {
                messageId = firstMatchingValue(responseBodyValues, MESSAGE_ID_KEYS);
            }
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = firstMatchingValue(responseBodyValues, CORRELATION_ID_KEYS);
            }
        }
        return new CpiSendResult(response.statusCode(), response.body(), response.headers(), messageId, correlationId, testRunId);
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", tokenProvider != null
                ? "Bearer " + tokenProvider.getAccessToken()
                : Credentials.basic(config.getInboundUser(), config.getInboundPassword()));
        headers.put("Accept", "application/json, text/plain, */*");
        return headers;
    }

    private String buildUrl(String resourcePath) {
        String baseUrl = config.getInboundBaseUrl().endsWith("/")
                ? config.getInboundBaseUrl().substring(0, config.getInboundBaseUrl().length() - 1)
                : config.getInboundBaseUrl();
        String normalizedPath = resourcePath == null ? "" : resourcePath.trim();
        if (normalizedPath.isEmpty()) {
            return baseUrl;
        }
        return normalizedPath.startsWith("/") ? baseUrl + normalizedPath : baseUrl + "/" + normalizedPath;
    }

    private String loadPayload(String localPath) {
        try {
            Path filePath = Path.of(localPath);
            if (Files.exists(filePath)) {
                return Files.readString(filePath, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // Fall back to classpath resolution below.
        }

        String resourcePath = localPath.startsWith("/") ? localPath.substring(1) : localPath;
        if (classLoader != null) {
            try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
                if (inputStream != null) {
                    return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read CPI request resource: " + localPath, e);
            }
        }

        throw new IllegalStateException("Unable to resolve CPI request payload from path or classpath resource: " + localPath);
    }

    private String detectContentType(String source, String payload) {
        String normalizedSource = source == null ? "" : source.toLowerCase();
        String trimmedPayload = payload == null ? "" : payload.trim();
        if (normalizedSource.endsWith(".xml") || trimmedPayload.startsWith("<")) {
            return "application/xml; charset=utf-8";
        }
        if (normalizedSource.endsWith(".json") || trimmedPayload.startsWith("{") || trimmedPayload.startsWith("[")) {
            return "application/json; charset=utf-8";
        }
        if (normalizedSource.endsWith(".txt")) {
            return "text/plain; charset=utf-8";
        }
        return "text/plain; charset=utf-8";
    }

    private Map<String, String> parseBodyFields(String body) {
        if (body == null || body.isBlank() || !body.trim().startsWith("{")) {
            return Map.of();
        }
        try {
            JSONObject jsonObject = new JSONObject(body);
            Map<String, String> values = new LinkedHashMap<>();
            for (java.util.Iterator<String> iterator = jsonObject.keys(); iterator.hasNext(); ) {
                String key = iterator.next();
                values.put(key, String.valueOf(jsonObject.opt(key)));
            }
            return values;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String firstMatchingValue(Map<String, String> values, List<String> candidateKeys) {
        for (String candidateKey : candidateKeys) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(candidateKey) && entry.getValue() != null && !entry.getValue().isBlank()) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }
}
