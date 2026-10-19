package me.cxdev.sapbtp.testing.communication;

import java.util.Map;
import java.util.Objects;

public record CpiSendResult(
        int statusCode, String body, Map<String, String> headers, String messageId, String correlationId, String testRunId) {
    public CpiSendResult {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    public String requireMessageId() {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalStateException("No messageId could be determined from the CPI response");
        }
        return messageId;
    }

    public String requireCorrelationId() {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalStateException("No correlationId could be determined from the CPI response");
        }
        return correlationId;
    }

    public String requireTestRunId() {
        if (testRunId == null || testRunId.isBlank()) {
            throw new IllegalStateException("No testRunId (x-test-run-id) could be determined from the CPI response");
        }
        return testRunId;
    }

    public String getHeader(String headerName) {
        return headers.entrySet().stream()
                .filter(entry -> Objects.equals(entry.getKey(), headerName) || entry.getKey().equalsIgnoreCase(headerName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
