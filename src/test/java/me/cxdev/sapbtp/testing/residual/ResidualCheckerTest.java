package me.cxdev.sapbtp.testing.residual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import me.cxdev.sapbtp.testing.collector.CollectedDocument;
import me.cxdev.sapbtp.testing.ledger.Ledger;
import org.junit.jupiter.api.Test;

class ResidualCheckerTest {
    private final ResidualChecker checker = new ResidualChecker();

    @Test
    void allMatchedDocumentsProduceNoResiduals() {
        Ledger ledger = new Ledger();
        ledger.register("corr-1", "test-case-1");
        ledger.recordMessageIds("corr-1", List.of("msg-1"));
        List<CollectedDocument> documents = List.of(new CollectedDocument("msg-1", 0, "<payload/>", Map.of()));

        List<ResidualDocument> residuals = checker.checkResiduals(ledger, documents);

        assertTrue(residuals.isEmpty());
    }

    @Test
    void unregisteredMessageIdAppearsAsResidualRegardlessOfHeaders() {
        Ledger ledger = new Ledger();
        ledger.register("corr-1", "test-case-1");
        ledger.recordMessageIds("corr-1", List.of("msg-1"));
        List<CollectedDocument> documents = List.of(
                new CollectedDocument("msg-1", 0, "<payload/>", Map.of()),
                new CollectedDocument("msg-2", 0, "<payload/>", Map.of()));

        List<ResidualDocument> residuals = checker.checkResiduals(ledger, documents);

        assertEquals(1, residuals.size());
        assertEquals("msg-2", residuals.get(0).messageId());
    }
}
