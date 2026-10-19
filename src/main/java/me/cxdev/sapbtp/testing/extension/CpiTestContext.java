package me.cxdev.sapbtp.testing.extension;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import me.cxdev.sapbtp.testing.collector.CollectedDocument;
import me.cxdev.sapbtp.testing.collector.CollectorClient;
import me.cxdev.sapbtp.testing.communication.CpiCommunicationClient;
import me.cxdev.sapbtp.testing.communication.CpiSendResult;
import me.cxdev.sapbtp.testing.config.CpiTestConfig;
import me.cxdev.sapbtp.testing.ledger.Ledger;
import me.cxdev.sapbtp.testing.monitoring.CpiMonitoringClient;
import me.cxdev.sapbtp.testing.monitoring.MonitoringEntry;
import me.cxdev.sapbtp.testing.residual.ResidualChecker;

public class CpiTestContext {
    private final String runId;
    private final CpiTestConfig config;
    private final Ledger ledger;
    private final CpiMonitoringClient monitoringClient;
    private final CpiCommunicationClient cpiCommunicationClient;
    private final CollectorClient collectorClient;
    private final ResidualChecker residualChecker;
    private final CopyOnWriteArrayList<CollectedDocument> collectedDocuments = new CopyOnWriteArrayList<>();
    private final Set<String> testRunIdsForCurrentTest = ConcurrentHashMap.newKeySet();

    public CpiTestContext(
            String runId,
            CpiTestConfig config,
            Ledger ledger,
            CpiMonitoringClient monitoringClient,
            CpiCommunicationClient cpiCommunicationClient,
            CollectorClient collectorClient,
            ResidualChecker residualChecker) {
        this.runId = runId;
        this.config = config;
        this.ledger = ledger;
        this.monitoringClient = monitoringClient;
        this.cpiCommunicationClient = cpiCommunicationClient;
        this.collectorClient = collectorClient;
        this.residualChecker = residualChecker;
    }

    public String getRunId() {
        return runId;
    }

    public CpiTestConfig getConfig() {
        return config;
    }

    public Ledger getLedger() {
        return ledger;
    }

    public CpiMonitoringClient getMonitoringClient() {
        return monitoringClient;
    }

    public CpiCommunicationClient getCpiCommunicationClient() {
        return cpiCommunicationClient;
    }

    public CollectorClient getCollectorClient() {
        return collectorClient;
    }

    public ResidualChecker getResidualChecker() {
        return residualChecker;
    }

    public void recordDocuments(List<CollectedDocument> documents) {
        collectedDocuments.addAll(documents);
    }

    public List<CollectedDocument> getCollectedDocuments() {
        return List.copyOf(collectedDocuments);
    }

    public CpiSendResult sendFileRequestToCpi(String resourcePathOnCpi, String localFilePath) {
        return cpiCommunicationClient.sendFileRequest(resourcePathOnCpi, localFilePath);
    }

    public CpiSendResult sendRawRequestToCpi(String resourcePathOnCpi, String payload) {
        return cpiCommunicationClient.sendRawRequest(resourcePathOnCpi, payload);
    }

    public String getCorrelationIdForMessageId(String messageId, String testRunId) {
        return cpiCommunicationClient.getCorrelationIdForMessageId(messageId, testRunId);
    }

    public List<MonitoringEntry> waitForCompletion(String correlationId, String testRunId) {
        return cpiCommunicationClient.waitForCompletion(correlationId, testRunId);
    }

    public List<MonitoringEntry> waitForCompletionForMessageId(String messageId, String testRunId) {
        return cpiCommunicationClient.waitForCompletionForMessageId(messageId, testRunId);
    }

    public void recordTestRunId(String testRunId) {
        if (testRunId != null && !testRunId.isBlank()) {
            testRunIdsForCurrentTest.add(testRunId);
        }
    }

    public void resetTestRunIdsForCurrentTest() {
        testRunIdsForCurrentTest.clear();
    }

    public void deleteCollectorRunsForCurrentTest() {
        for (String testRunId : testRunIdsForCurrentTest) {
            collectorClient.deleteRun(testRunId);
        }
        testRunIdsForCurrentTest.clear();
    }
}
