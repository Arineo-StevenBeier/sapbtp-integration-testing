package me.cxdev.sapbtp.testing.dsl;

import java.util.List;
import java.util.Objects;

import me.cxdev.sapbtp.testing.collector.CollectedDocument;

public final class CpiTestResponse {
    private final String correlationId;
    private final List<String> messageIds;
    private final List<CollectedDocument> documents;
    private final String testRunId;

    public CpiTestResponse(String correlationId, List<String> messageIds, List<CollectedDocument> documents, String testRunId) {
        this.correlationId = correlationId;
        this.messageIds = List.copyOf(messageIds);
        this.documents = List.copyOf(documents);
        this.testRunId = testRunId;
    }

    public List<CollectedDocument> getDocuments() {
        return documents;
    }

    public int size() {
        return documents.size();
    }

    public DocumentAssertion document(int index) {
        if (index < 0 || index >= documents.size()) {
            throw new AssertionError(
                    "Document index out of bounds: " + index + " for messageIds=" + messageIds + ", testRunId=" + testRunId);
        }
        return new DocumentAssertion(index, documents.get(index));
    }

    public final class DocumentAssertion {
        private final int index;
        private final CollectedDocument document;

        private DocumentAssertion(int index, CollectedDocument document) {
            this.index = index;
            this.document = document;
        }

        public CpiTestResponse hasPayload(String expectedPayload) {
            if (!Objects.equals(expectedPayload, document.payload())) {
                throw new AssertionError("Payload mismatch at document index " + index + " for correlationId=" + correlationId
                        + ", messageId=" + document.messageId() + ", testRunId=" + testRunId
                        + ". Expected <" + expectedPayload + "> but was <" + document.payload() + ">");
            }
            return CpiTestResponse.this;
        }

        public CpiTestResponse hasHeader(String headerName, String expectedValue) {
            String actualValue = document.headers().get(headerName);
            if (!Objects.equals(expectedValue, actualValue)) {
                throw new AssertionError("Header mismatch for '" + headerName + "' at document index " + index
                        + " for correlationId=" + correlationId + ", messageId=" + document.messageId() + ", testRunId=" + testRunId
                        + ". Expected <" + expectedValue + "> but was <" + actualValue + ">");
            }
            return CpiTestResponse.this;
        }
    }
}
