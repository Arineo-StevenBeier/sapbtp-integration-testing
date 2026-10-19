package me.cxdev.sapbtp.testing.extension;

import java.util.List;
import java.util.stream.Collectors;

import me.cxdev.sapbtp.testing.collector.CollectorClient;
import me.cxdev.sapbtp.testing.communication.CpiCommunicationClient;
import me.cxdev.sapbtp.testing.config.ConfigLoader;
import me.cxdev.sapbtp.testing.config.CpiTestConfig;
import me.cxdev.sapbtp.testing.dsl.CpiTestDsl;
import me.cxdev.sapbtp.testing.http.OkHttpTransport;
import me.cxdev.sapbtp.testing.ledger.Ledger;
import me.cxdev.sapbtp.testing.monitoring.CpiMonitoringClient;
import me.cxdev.sapbtp.testing.residual.ResidualChecker;
import me.cxdev.sapbtp.testing.residual.ResidualDocument;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestWatcher;

public class CpiTestExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, TestWatcher, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(CpiTestExtension.class);
    private static final String CONTEXT_RESOURCE_KEY = "cpi-test-context-resource";

    @Override
    public void beforeAll(ExtensionContext context) {
        ContextResource resource = context.getRoot().getStore(NAMESPACE)
                .getOrComputeIfAbsent(CONTEXT_RESOURCE_KEY, key -> createResource(), ContextResource.class);
        CpiTestDsl.bind(resource.context());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        // Residual verification runs once when the root store is closed.
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        resource(context).context().resetTestRunIdsForCurrentTest();
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        // Only clean up the Collector run when the test actually passed, so a
        // failing test's data stays available in the Collector for debugging.
        resource(context).context().deleteCollectorRunsForCurrentTest();
    }

    private ContextResource resource(ExtensionContext context) {
        return context.getRoot().getStore(NAMESPACE).get(CONTEXT_RESOURCE_KEY, ContextResource.class);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> parameterType = parameterContext.getParameter().getType();
        return parameterType == CpiTestContext.class
                || parameterType == CpiTestConfig.class
                || parameterType == CpiCommunicationClient.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        ContextResource resource = resource(extensionContext);
        if (resource == null) {
            throw new ParameterResolutionException("CpiTestContext has not been initialized");
        }
        Class<?> parameterType = parameterContext.getParameter().getType();
        if (parameterType == CpiTestContext.class) {
            return resource.context();
        }
        if (parameterType == CpiTestConfig.class) {
            return resource.context().getConfig();
        }
        if (parameterType == CpiCommunicationClient.class) {
            return resource.context().getCpiCommunicationClient();
        }
        throw new ParameterResolutionException("Unsupported parameter type: " + parameterType.getName());
    }

    private ContextResource createResource() {
        CpiTestConfig config = new ConfigLoader().load();
        OkHttpTransport transport = new OkHttpTransport();
        CpiMonitoringClient monitoringClient = new CpiMonitoringClient(transport, config);
        CpiTestContext context = new CpiTestContext(
                RunIdGenerator.generate(),
                config,
                new Ledger(),
                monitoringClient,
                new CpiCommunicationClient(transport, config, monitoringClient),
                new CollectorClient(transport, config.getCollectorBaseUrl()),
                new ResidualChecker());
        return new ContextResource(context);
    }

    private static final class ContextResource implements ExtensionContext.Store.CloseableResource {
        private final CpiTestContext context;

        private ContextResource(CpiTestContext context) {
            this.context = context;
        }

        private CpiTestContext context() {
            return context;
        }

        @Override
        public void close() {
            List<ResidualDocument> residuals = context.getResidualChecker()
                    .checkResiduals(context.getLedger(), context.getCollectedDocuments());
            CpiTestDsl.clear();
            if (!residuals.isEmpty()) {
                String details = residuals.stream()
                        .map(residual -> "messageId=" + residual.messageId()
                                + ", correlationId=" + residual.correlationId()
                                + ", assignedTestCase=" + residual.assignedTestCase())
                        .collect(Collectors.joining("; "));
                throw new AssertionError("Residual documents detected for runId=" + context.getRunId() + ": " + details);
            }
        }
    }
}
