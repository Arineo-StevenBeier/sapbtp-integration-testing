package me.cxdev.sapbtp.testing.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;

import me.cxdev.sapbtp.testing.collector.CollectorClient;
import me.cxdev.sapbtp.testing.communication.CpiCommunicationClient;
import me.cxdev.sapbtp.testing.config.CpiTestConfig;
import me.cxdev.sapbtp.testing.http.OkHttpTransport;
import me.cxdev.sapbtp.testing.ledger.Ledger;
import me.cxdev.sapbtp.testing.monitoring.CpiMonitoringClient;
import me.cxdev.sapbtp.testing.residual.ResidualChecker;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class CpiTestContextTest {
    @Test
    void deleteCollectorRunsForCurrentTestDeletesEachRecordedRunAndClears() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(204));
            server.enqueue(new MockResponse().setResponseCode(204));
            server.start();

            CpiTestContext context = newContext(server);

            context.recordTestRunId("run-a");
            context.recordTestRunId("run-b");
            context.deleteCollectorRunsForCurrentTest();

            assertEquals(2, server.getRequestCount());
            RecordedRequest first = server.takeRequest();
            RecordedRequest second = server.takeRequest();
            assertEquals("DELETE", first.getMethod());
            assertEquals("DELETE", second.getMethod());
        }
    }

    @Test
    void resetTestRunIdsForCurrentTestClearsWithoutDeleting() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();

            CpiTestContext context = newContext(server);

            context.recordTestRunId("run-a");
            context.resetTestRunIdsForCurrentTest();
            context.deleteCollectorRunsForCurrentTest();

            assertEquals(0, server.getRequestCount());
        }
    }

    private CpiTestContext newContext(MockWebServer server) {
        CpiTestConfig config = new CpiTestConfig(
                "https://monitoring.example.test", "user", "password", server.url("/").toString(), 1, 50);
        OkHttpTransport transport = new OkHttpTransport();
        CpiMonitoringClient monitoringClient = new CpiMonitoringClient(transport, config);
        return new CpiTestContext(
                "local-run-id",
                config,
                new Ledger(),
                monitoringClient,
                new CpiCommunicationClient(transport, config, monitoringClient),
                new CollectorClient(transport, server.url("/").toString()),
                new ResidualChecker());
    }
}
