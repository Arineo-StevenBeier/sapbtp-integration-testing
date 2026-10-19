package me.cxdev.sapbtp.testing.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import me.cxdev.sapbtp.testing.config.CpiTestConfig;
import me.cxdev.sapbtp.testing.http.OkHttpTransport;
import me.cxdev.sapbtp.testing.monitoring.CpiMonitoringClient;
import me.cxdev.sapbtp.testing.monitoring.MonitoringEntry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class CpiCommunicationClientTest {
    @Test
    void sendRawRequestPostsPayloadAndExtractsMessageIdFromHeaders() throws Exception {
        try (MockWebServer inboundServer = new MockWebServer();
                MockWebServer monitoringServer = new MockWebServer()) {
            inboundServer.enqueue(new MockResponse()
                    .setHeader("SAP_MessageProcessingLogID", "msg-001")
                    .setHeader("CorrelationId", "corr-001")
                    .setHeader("x-test-run-id", "run-abc")
                    .setBody("{\"accepted\":true}"));
            inboundServer.start();
            monitoringServer.start();

            CpiTestConfig config = new CpiTestConfig(
                    monitoringServer.url("/").toString(),
                    "monitoring-user",
                    "monitoring-password",
                    inboundServer.url("/").toString(),
                    "inbound-user",
                    "inbound-password",
                    "http://collector",
                    1,
                    50);
            CpiCommunicationClient client = new CpiCommunicationClient(
                    new OkHttpTransport(),
                    config,
                    new CpiMonitoringClient(new OkHttpTransport(), config));

            CpiSendResult response = client.sendRawRequest("/iflow/orders", "<order/>");
            RecordedRequest request = inboundServer.takeRequest();

            assertTrue(response.isSuccessful());
            assertEquals("msg-001", response.requireMessageId());
            assertEquals("corr-001", response.requireCorrelationId());
            assertEquals("run-abc", response.requireTestRunId());
            assertEquals("/iflow/orders", request.getPath());
            assertEquals("application/xml; charset=utf-8", request.getHeader("Content-Type"));
        }
    }

    @Test
    void sendFileRequestLoadsPayloadFromFileSystem() throws Exception {
        try (MockWebServer inboundServer = new MockWebServer();
                MockWebServer monitoringServer = new MockWebServer()) {
            inboundServer.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"messageId\":\"msg-002\",\"CorrelationId\":\"corr-002\"}"));
            inboundServer.start();
            monitoringServer.start();

            Path payloadFile = Files.createTempFile("cpi-request-", ".xml");
            Files.writeString(payloadFile, "<invoice/>");

            CpiTestConfig config = new CpiTestConfig(
                    monitoringServer.url("/").toString(),
                    "monitoring-user",
                    "monitoring-password",
                    inboundServer.url("/").toString(),
                    "inbound-user",
                    "inbound-password",
                    "http://collector",
                    1,
                    50);
            CpiCommunicationClient client = new CpiCommunicationClient(
                    new OkHttpTransport(),
                    config,
                    new CpiMonitoringClient(new OkHttpTransport(), config));

            CpiSendResult response = client.sendFileRequest("iflow/invoices", payloadFile.toString());
            RecordedRequest request = inboundServer.takeRequest();

            assertEquals("msg-002", response.requireMessageId());
            assertEquals("/iflow/invoices", request.getPath());
            assertEquals("<invoice/>", request.getBody().readUtf8());
        }
    }

    @Test
    void sendRawRequestUsesOAuth2BearerTokenWhenConfigured() throws Exception {
        try (MockWebServer inboundServer = new MockWebServer();
                MockWebServer monitoringServer = new MockWebServer();
                MockWebServer tokenServer = new MockWebServer()) {
            tokenServer.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"access_token\":\"access-token-1\",\"expires_in\":3600}"));
            tokenServer.start();
            inboundServer.enqueue(new MockResponse()
                    .setHeader("SAP_MessageProcessingLogID", "msg-004")
                    .setHeader("CorrelationId", "corr-004")
                    .setBody("{\"accepted\":true}"));
            inboundServer.start();
            monitoringServer.start();

            CpiTestConfig config = new CpiTestConfig(
                    monitoringServer.url("/").toString(),
                    null,
                    null,
                    inboundServer.url("/").toString(),
                    null,
                    null,
                    "http://collector",
                    1,
                    50,
                    tokenServer.url("/oauth/token").toString(),
                    "client-id",
                    "client-secret");
            CpiCommunicationClient client = new CpiCommunicationClient(
                    new OkHttpTransport(),
                    config,
                    new CpiMonitoringClient(new OkHttpTransport(), config));

            client.sendRawRequest("/iflow/orders", "<order/>");
            RecordedRequest request = inboundServer.takeRequest();

            assertEquals("Bearer access-token-1", request.getHeader("Authorization"));
        }
    }

    @Test
    void waitForCompletionForMessageIdResolvesCorrelationIdAndWaitsForTerminalStates() {
        RecordingMonitoringClient monitoringClient = new RecordingMonitoringClient();
        CpiTestConfig config = new CpiTestConfig(
                "https://monitoring.example.test",
                "monitoring-user",
                "monitoring-password",
                "https://inbound.example.test",
                "inbound-user",
                "inbound-password",
                "http://collector",
                1,
                50);
        CpiCommunicationClient client = new CpiCommunicationClient(new OkHttpTransport(), config, monitoringClient);

        List<MonitoringEntry> entries = client.waitForCompletionForMessageId("msg-003", "run-1");

        assertEquals("msg-003", monitoringClient.lastMessageId);
        assertEquals("corr-003", monitoringClient.lastCorrelationId);
        assertEquals(1, entries.size());
        assertEquals("COMPLETED", entries.get(0).status());
    }

    private static final class RecordingMonitoringClient extends CpiMonitoringClient {
        private String lastMessageId;
        private String lastCorrelationId;

        private RecordingMonitoringClient() {
            super(
                    new OkHttpTransport(),
                    new CpiTestConfig(
                            "https://monitoring.example.test",
                            "user",
                            "password",
                            "http://collector",
                            1,
                            50));
        }

        @Override
        public String getCorrelationIdForMessageId(String messageId, String runId) {
            this.lastMessageId = messageId;
            return "corr-003";
        }

        @Override
        public List<MonitoringEntry> awaitCompletion(String correlationId, String runId) {
            this.lastCorrelationId = correlationId;
            return List.of(new MonitoringEntry("msg-003", correlationId, "COMPLETED"));
        }
    }
}
