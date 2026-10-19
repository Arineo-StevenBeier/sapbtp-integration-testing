package me.cxdev.sapbtp.testing.config;

public final class CpiTestConfig {
    private final String monitoringBaseUrl;
    private final String monitoringUser;
    private final String monitoringPassword;
    private final String inboundBaseUrl;
    private final String inboundUser;
    private final String inboundPassword;
    private final String collectorBaseUrl;
    private final int pollingIntervalMs;
    private final int pollingTimeoutMs;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String monitoringClientId;
    private final String monitoringClientSecret;

    public CpiTestConfig(
            String monitoringBaseUrl,
            String monitoringUser,
            String monitoringPassword,
            String collectorBaseUrl,
            Integer pollingIntervalMs,
            Integer pollingTimeoutMs) {
        this(
                monitoringBaseUrl,
                monitoringUser,
                monitoringPassword,
                monitoringBaseUrl,
                monitoringUser,
                monitoringPassword,
                collectorBaseUrl,
                pollingIntervalMs,
                pollingTimeoutMs,
                null,
                null,
                null,
                null,
                null);
    }

    public CpiTestConfig(
            String monitoringBaseUrl,
            String monitoringUser,
            String monitoringPassword,
            String inboundBaseUrl,
            String inboundUser,
            String inboundPassword,
            String collectorBaseUrl,
            Integer pollingIntervalMs,
            Integer pollingTimeoutMs) {
        this(
                monitoringBaseUrl,
                monitoringUser,
                monitoringPassword,
                inboundBaseUrl,
                inboundUser,
                inboundPassword,
                collectorBaseUrl,
                pollingIntervalMs,
                pollingTimeoutMs,
                null,
                null,
                null,
                null,
                null);
    }

    public CpiTestConfig(
            String monitoringBaseUrl,
            String monitoringUser,
            String monitoringPassword,
            String inboundBaseUrl,
            String inboundUser,
            String inboundPassword,
            String collectorBaseUrl,
            Integer pollingIntervalMs,
            Integer pollingTimeoutMs,
            String tokenUrl,
            String clientId,
            String clientSecret) {
        this(
                monitoringBaseUrl,
                monitoringUser,
                monitoringPassword,
                inboundBaseUrl,
                inboundUser,
                inboundPassword,
                collectorBaseUrl,
                pollingIntervalMs,
                pollingTimeoutMs,
                tokenUrl,
                clientId,
                clientSecret,
                null,
                null);
    }

    public CpiTestConfig(
            String monitoringBaseUrl,
            String monitoringUser,
            String monitoringPassword,
            String inboundBaseUrl,
            String inboundUser,
            String inboundPassword,
            String collectorBaseUrl,
            Integer pollingIntervalMs,
            Integer pollingTimeoutMs,
            String tokenUrl,
            String clientId,
            String clientSecret,
            String monitoringClientId,
            String monitoringClientSecret) {
        this.monitoringBaseUrl = monitoringBaseUrl;
        this.monitoringUser = monitoringUser;
        this.monitoringPassword = monitoringPassword;
        this.inboundBaseUrl = isBlank(inboundBaseUrl) ? monitoringBaseUrl : inboundBaseUrl;
        this.inboundUser = isBlank(inboundUser) ? monitoringUser : inboundUser;
        this.inboundPassword = isBlank(inboundPassword) ? monitoringPassword : inboundPassword;
        this.collectorBaseUrl = collectorBaseUrl;
        this.pollingIntervalMs = pollingIntervalMs == null ? 5000 : pollingIntervalMs;
        this.pollingTimeoutMs = pollingTimeoutMs == null ? 60000 : pollingTimeoutMs;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.monitoringClientId = monitoringClientId;
        this.monitoringClientSecret = monitoringClientSecret;
    }

    public String getMonitoringBaseUrl() {
        return monitoringBaseUrl;
    }

    public String getMonitoringUser() {
        return monitoringUser;
    }

    public String getMonitoringPassword() {
        return monitoringPassword;
    }

    public String getInboundBaseUrl() {
        return inboundBaseUrl;
    }

    public String getInboundUser() {
        return inboundUser;
    }

    public String getInboundPassword() {
        return inboundPassword;
    }

    public String getCollectorBaseUrl() {
        return collectorBaseUrl;
    }

    public int getPollingIntervalMs() {
        return pollingIntervalMs;
    }

    public int getPollingTimeoutMs() {
        return pollingTimeoutMs;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getMonitoringClientId() {
        return monitoringClientId;
    }

    public String getMonitoringClientSecret() {
        return monitoringClientSecret;
    }

    public boolean isOAuth2Configured() {
        return !isBlank(tokenUrl) && !isBlank(clientId) && !isBlank(clientSecret);
    }

    public boolean isMonitoringOAuth2Configured() {
        return !isBlank(tokenUrl) && !isBlank(monitoringClientId) && !isBlank(monitoringClientSecret);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
