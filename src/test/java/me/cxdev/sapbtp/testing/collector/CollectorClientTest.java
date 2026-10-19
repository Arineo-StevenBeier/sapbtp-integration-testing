package me.cxdev.sapbtp.testing.collector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import me.cxdev.sapbtp.testing.http.OkHttpTransport;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class CollectorClientTest {
    @Test
    void fetchDocumentsLoadsPayloadAndHeadersForEachSequence() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(
                    "{\"messageId\":\"msg-001\",\"sequences\":[{\"seqNo\":1,\"contentType\":\"application/xml\"},"
                            + "{\"seqNo\":2,\"contentType\":\"application/xml\"}]}"));
            server.enqueue(new MockResponse().setBody("<order/>"));
            server.enqueue(new MockResponse().setBody("{\"CorrelationId\":\"corr-1\",\"content-type\":\"application/xml\"}"));
            server.enqueue(new MockResponse().setBody("<invoice/>"));
            server.enqueue(new MockResponse().setBody("{\"CorrelationId\":\"corr-1\",\"content-type\":\"application/xml\"}"));
            server.start();

            CollectorClient client = new CollectorClient(new OkHttpTransport(), server.url("/").toString());

            List<CollectedDocument> documents = client.fetchDocuments("run-1", List.of("msg-001"));

            assertEquals(2, documents.size());
            assertEquals(1, documents.get(0).sequence());
            assertEquals("<order/>", documents.get(0).payload());
            assertEquals("application/xml", documents.get(0).headers().get("content-type"));
            assertEquals(2, documents.get(1).sequence());
            assertEquals("<invoice/>", documents.get(1).payload());

            assertEquals("/runs/run-1/messages/msg-001", server.takeRequest().getPath());
            RecordedRequest payload1 = server.takeRequest();
            assertEquals("/runs/run-1/messages/msg-001/payload?seq=1", payload1.getPath());
            RecordedRequest header1 = server.takeRequest();
            assertEquals("/runs/run-1/messages/msg-001/header?seq=1", header1.getPath());
            assertEquals("/runs/run-1/messages/msg-001/payload?seq=2", server.takeRequest().getPath());
            assertEquals("/runs/run-1/messages/msg-001/header?seq=2", server.takeRequest().getPath());
        }
    }

    @Test
    void fetchDocumentsThrowsWhenMetadataRequestFails() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"error\":\"Message not found\"}"));
            server.start();

            CollectorClient client = new CollectorClient(new OkHttpTransport(), server.url("/").toString());

            assertThrows(IllegalStateException.class, () -> client.fetchDocuments("run-1", List.of("msg-missing")));
        }
    }

    @Test
    void deleteRunSendsDeleteToRunsEndpoint() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(204));
            server.start();

            CollectorClient client = new CollectorClient(new OkHttpTransport(), server.url("/").toString());

            client.deleteRun("run-1");

            RecordedRequest request = server.takeRequest();
            assertEquals("DELETE", request.getMethod());
            assertEquals("/runs/run-1", request.getPath());
        }
    }

    @Test
    void deleteRunIgnoresAlreadyGoneRun() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404));
            server.start();

            CollectorClient client = new CollectorClient(new OkHttpTransport(), server.url("/").toString());

            client.deleteRun("run-1");
        }
    }

    @Test
    void deleteRunThrowsOnUnexpectedFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(503));
            server.start();

            CollectorClient client = new CollectorClient(new OkHttpTransport(), server.url("/").toString());

            assertThrows(IllegalStateException.class, () -> client.deleteRun("run-1"));
        }
    }
}
