package me.cxdev.sapbtp.testing.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import me.cxdev.sapbtp.testing.config.CpiTestConfig;
import me.cxdev.sapbtp.testing.http.OkHttpTransport;
import okhttp3.Credentials;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class CpiMonitoringClientTest {
    @Test
    void fetchMessageIdsUsesMonitoringApiAndBasicAuth() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"d\":{\"results\":[{\"MessageGuid\":\"msg-001\"}]}}"));
            server.start();

            CpiTestConfig config = new CpiTestConfig(
                    server.url("/").toString(),
                    "alice",
                    "secret",
                    "http://collector",
                    10,
                    50);
            CpiMonitoringClient client = new CpiMonitoringClient(new OkHttpTransport(), config);

            List<String> messageIds = client.fetchMessageIds("corr-123", "run-1");
            RecordedRequest request = server.takeRequest();

            assertEquals(List.of("msg-001"), messageIds);
            assertEquals(Credentials.basic("alice", "secret"), request.getHeader("Authorization"));
            assertTrue(request.getPath().contains("MessageProcessingLogs"));
            assertTrue(request.getPath().contains("corr-123"));
        }
    }

    @Test
    void fetchMessageIdsUsesOAuth2BearerTokenWhenConfigured() throws Exception {
        try (MockWebServer server = new MockWebServer(); MockWebServer tokenServer = new MockWebServer()) {
            tokenServer.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"access_token\":\"access-token-1\",\"expires_in\":3600}"));
            tokenServer.start();
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"d\":{\"results\":[{\"MessageGuid\":\"msg-001\"}]}}"));
            server.start();

            CpiTestConfig config = new CpiTestConfig(
                    server.url("/").toString(),
                    null,
                    null,
                    server.url("/").toString(),
                    null,
                    null,
                    "http://collector",
                    10,
                    50,
                    tokenServer.url("/oauth/token").toString(),
                    null,
                    null,
                    "monitoring-client-id",
                    "monitoring-client-secret");
            CpiMonitoringClient client = new CpiMonitoringClient(new OkHttpTransport(), config);

            client.fetchMessageIds("corr-123", "run-1");
            RecordedRequest request = server.takeRequest();

            assertEquals("Bearer access-token-1", request.getHeader("Authorization"));
        }
    }

    @Test
    void fetchMessageIdsRetriesUntilMessagesAreAvailable() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"d\":{\"results\":[]}}"));
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"d\":{\"results\":[{\"MessageGuid\":\"msg-002\"}]}}"));
            server.start();

            CpiTestConfig config = new CpiTestConfig(
                    server.url("/").toString(),
                    "alice",
                    "secret",
                    "http://collector",
                    1,
                    100);
            CpiMonitoringClient client = new CpiMonitoringClient(new OkHttpTransport(), config);

            List<String> messageIds = client.fetchMessageIds("corr-123", "run-1");

            assertEquals(List.of("msg-002"), messageIds);
            assertEquals(2, server.getRequestCount());
        }
    }

    @Test
    void getCorrelationIdForMessageIdUsesMonitoringApi() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"d\":{\"results\":[{\"MessageGuid\":\"msg-001\",\"CorrelationId\":\"corr-123\",\"Status\":\"COMPLETED\"}]}}"));
            server.start();

            CpiTestConfig config = new CpiTestConfig(
                    server.url("/").toString(),
                    "alice",
                    "secret",
                    "http://collector",
                    10,
                    50);
            CpiMonitoringClient client = new CpiMonitoringClient(new OkHttpTransport(), config);

            String correlationId = client.getCorrelationIdForMessageId("msg-001", "run-1");

            assertEquals("corr-123", correlationId);
        }
    }

    @Test
    void awaitCompletionRetriesUntilAllMessagesReachTerminalState() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"d\":{\"results\":[{\"MessageGuid\":\"msg-001\",\"CorrelationId\":\"corr-123\",\"Status\":\"PROCESSING\"}]}}"));
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"d\":{\"results\":[{\"MessageGuid\":\"msg-001\",\"CorrelationId\":\"corr-123\",\"Status\":\"COMPLETED\"},{\"MessageGuid\":\"msg-002\",\"CorrelationId\":\"corr-123\",\"Status\":\"FAILED\"}]}}"));
            server.start();

            CpiTestConfig config = new CpiTestConfig(
                    server.url("/").toString(),
                    "alice",
                    "secret",
                    "http://collector",
                    1,
                    100);
            CpiMonitoringClient client = new CpiMonitoringClient(new OkHttpTransport(), config);

            List<MonitoringEntry> entries = client.awaitCompletion("corr-123", "run-1");

            assertEquals(2, entries.size());
            assertEquals(2, server.getRequestCount());
        }
    }

    @Test
    void awaitCompletionTimesOutWhenStatusesStayNonTerminal() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"d\":{\"results\":[{\"MessageGuid\":\"msg-001\",\"CorrelationId\":\"corr-123\",\"Status\":\"PROCESSING\"}]}}"));
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"d\":{\"results\":[{\"MessageGuid\":\"msg-001\",\"CorrelationId\":\"corr-123\",\"Status\":\"PROCESSING\"}]}}"));
            server.start();

            CpiTestConfig config = new CpiTestConfig(
                    server.url("/").toString(),
                    "alice",
                    "secret",
                    "http://collector",
                    1,
                    5);
            CpiMonitoringClient client = new CpiMonitoringClient(new OkHttpTransport(), config);

            assertThrows(IllegalStateException.class, () -> client.awaitCompletion("corr-123", "run-1"));
        }
    }
}
