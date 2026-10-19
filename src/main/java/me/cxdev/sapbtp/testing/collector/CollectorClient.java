package me.cxdev.sapbtp.testing.collector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import me.cxdev.sapbtp.testing.http.HttpResponse;
import me.cxdev.sapbtp.testing.http.HttpTransport;
import org.json.JSONArray;
import org.json.JSONObject;

public class CollectorClient {
    private final HttpTransport transport;
    private final String collectorBaseUrl;

    public CollectorClient(HttpTransport transport, String collectorBaseUrl) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.collectorBaseUrl = normalizeBaseUrl(collectorBaseUrl);
    }

    public List<CollectedDocument> fetchDocuments(String testRunId, List<String> messageIds) {
        Objects.requireNonNull(testRunId, "testRunId");
        List<CollectedDocument> documents = new ArrayList<>();
        for (String messageId : messageIds) {
            for (int sequence : fetchSequenceNumbers(testRunId, messageId)) {
                String payload = fetchPayload(testRunId, messageId, sequence);
                Map<String, String> headers = fetchHeaders(testRunId, messageId, sequence);
                documents.add(new CollectedDocument(messageId, sequence, payload, headers));
            }
        }
        return documents;
    }

    private List<Integer> fetchSequenceNumbers(String testRunId, String messageId) {
        HttpResponse response = transport.get(messageUrl(testRunId, messageId), Map.of("Accept", "application/json"));
        ensureSuccess(response, testRunId, messageId, "metadata");
        JSONObject json;
        try {
            json = new JSONObject(response.body());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Collector metadata response was not valid JSON for testRunId=" + testRunId + ", messageId=" + messageId, e);
        }
        List<Integer> sequenceNumbers = new ArrayList<>();
        JSONArray sequences = json.optJSONArray("sequences");
        if (sequences != null) {
            for (int i = 0; i < sequences.length(); i++) {
                JSONObject entry = sequences.optJSONObject(i);
                if (entry != null) {
                    sequenceNumbers.add(entry.optInt("seqNo", i + 1));
                }
            }
        }
        return sequenceNumbers;
    }

    private String fetchPayload(String testRunId, String messageId, int sequence) {
        HttpResponse response = transport.get(
                messageUrl(testRunId, messageId) + "/payload?seq=" + sequence, Map.of("Accept", "*/*"));
        ensureSuccess(response, testRunId, messageId, "payload");
        return response.body();
    }

    private Map<String, String> fetchHeaders(String testRunId, String messageId, int sequence) {
        HttpResponse response = transport.get(
                messageUrl(testRunId, messageId) + "/header?seq=" + sequence, Map.of("Accept", "application/json"));
        ensureSuccess(response, testRunId, messageId, "header");
        try {
            return jsonObjectToMap(new JSONObject(response.body()));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Collector header response was not valid JSON for testRunId=" + testRunId + ", messageId=" + messageId, e);
        }
    }

    private Map<String, String> jsonObjectToMap(JSONObject jsonObject) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Iterator<String> iterator = jsonObject.keys(); iterator.hasNext(); ) {
            String key = iterator.next();
            headers.put(key, String.valueOf(jsonObject.opt(key)));
        }
        return headers;
    }

    public void deleteRun(String testRunId) {
        Objects.requireNonNull(testRunId, "testRunId");
        HttpResponse response = transport.delete(collectorBaseUrl + "/runs/" + testRunId, Map.of());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        if (response.statusCode() == 404) {
            return;
        }
        throw new IllegalStateException(
                "Collector run deletion failed for testRunId=" + testRunId + " with status " + response.statusCode());
    }

    private void ensureSuccess(HttpResponse response, String testRunId, String messageId, String resource) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        throw new IllegalStateException("Collector " + resource + " request failed for testRunId=" + testRunId
                + ", messageId=" + messageId + " with status " + response.statusCode());
    }

    private String messageUrl(String testRunId, String messageId) {
        return collectorBaseUrl + "/runs/" + testRunId + "/messages/" + messageId;
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
