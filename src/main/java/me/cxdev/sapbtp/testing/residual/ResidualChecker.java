package me.cxdev.sapbtp.testing.residual;

import java.util.ArrayList;
import java.util.List;

import me.cxdev.sapbtp.testing.collector.CollectedDocument;
import me.cxdev.sapbtp.testing.ledger.Ledger;
import me.cxdev.sapbtp.testing.ledger.LedgerEntry;

public class ResidualChecker {
    public List<ResidualDocument> checkResiduals(Ledger ledger, List<CollectedDocument> allDocuments) {
        List<LedgerEntry> entries = ledger.getAll();
        List<ResidualDocument> residuals = new ArrayList<>();
        for (CollectedDocument document : allDocuments) {
            boolean claimed = entries.stream().anyMatch(entry -> entry.messageIds().contains(document.messageId()));
            if (!claimed) {
                residuals.add(new ResidualDocument(document.messageId(), null, null));
            }
        }
        return residuals;
    }
}
