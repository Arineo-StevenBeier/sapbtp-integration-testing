package me.cxdev.sapbtp.testing.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public class ConfigLoader {
    private static final String PROPERTY_NAME = "cpi.test.profile";
    private static final String ENV_PREFIX = "CPI_TEST_";

    private final Path configDirectory;
    private final Map<String, String> environment;
    private final Properties properties;
    private final ClassLoader classLoader;

    public ConfigLoader() {
        this(null, System.getenv(), System.getProperties(), Thread.currentThread().getContextClassLoader());
    }

    public ConfigLoader(Path configDirectory, Map<String, String> environment, Properties properties) {
        this(configDirectory, environment, properties, Thread.currentThread().getContextClassLoader());
    }

    ConfigLoader(Path configDirectory, Map<String, String> environment, Properties properties, ClassLoader classLoader) {
        this.configDirectory = configDirectory;
        this.environment = Map.copyOf(environment);
        this.properties = properties;
        this.classLoader = classLoader;
    }

    public CpiTestConfig load() {
        return load(CpiTestProfile.from(properties.getProperty(PROPERTY_NAME, CpiTestProfile.LOCAL.fileSuffix())));
    }

    public CpiTestConfig load(CpiTestProfile profile) {
        Map<String, Object> values = new LinkedHashMap<>(loadYaml(profile));
        applyEnvironmentOverrides(values);
        return mapConfig(values);
    }

    private Map<String, Object> loadYaml(CpiTestProfile profile) {
        String profileSpecific = "sapbtp-integration-testing-config-" + profile.fileSuffix() + ".yaml";
        try (InputStream profileStream = openConfig(profileSpecific)) {
            if (profileStream != null) {
                return extractTestMap(profileStream);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to close configuration stream for profile " + profile, e);
        }

        try (InputStream defaultStream = openConfig("sapbtp-integration-testing-config.yaml")) {
            if (defaultStream == null) {
                throw new ConfigValidationException("Missing configuration resource: sapbtp-integration-testing-config.yaml");
            }
            return extractTestMap(defaultStream);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to close default configuration stream", e);
        }
    }

    private InputStream openConfig(String fileName) {
        try {
            if (configDirectory != null) {
                Path candidate = configDirectory.resolve(fileName);
                if (Files.exists(candidate)) {
                    return Files.newInputStream(candidate);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read configuration file " + fileName, e);
        }
        return classLoader == null ? null : classLoader.getResourceAsStream(fileName);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractTestMap(InputStream inputStream) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object loaded = yaml.load(inputStream);
        if (!(loaded instanceof Map<?, ?> root)) {
            return Map.of();
        }
        Object cpi = root.get("cpi");
        if (!(cpi instanceof Map<?, ?> cpiMap)) {
            return Map.of();
        }
        Object test = cpiMap.get("test");
        if (!(test instanceof Map<?, ?> testMap)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        testMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private void applyEnvironmentOverrides(Map<String, Object> values) {
        override(values, "monitoringBaseUrl", firstNonBlank(environment.get(ENV_PREFIX + "MONITORING_URL"), environment.get("CPI_MONITORING_URL")));
        override(values, "monitoringUser", environment.get(ENV_PREFIX + "MONITORING_USER"));
        override(values, "monitoringPassword", environment.get(ENV_PREFIX + "MONITORING_PASSWORD"));
        override(values, "inboundBaseUrl", firstNonBlank(environment.get(ENV_PREFIX + "INBOUND_URL"), environment.get("CPI_TENANT_URL")));
        override(values, "inboundUser", environment.get(ENV_PREFIX + "INBOUND_USER"));
        override(values, "inboundPassword", environment.get(ENV_PREFIX + "INBOUND_PASSWORD"));
        override(values, "collectorBaseUrl", firstNonBlank(environment.get(ENV_PREFIX + "COLLECTOR_URL"), environment.get("COLLECTOR_URL")));
        override(values, "tokenUrl", firstNonBlank(environment.get(ENV_PREFIX + "TOKEN_URL"), environment.get("CPI_TOKEN_URL")));
        override(values, "clientId", firstNonBlank(environment.get(ENV_PREFIX + "CLIENT_ID"), environment.get("CPI_CLIENT_ID")));
        override(values, "clientSecret", firstNonBlank(environment.get(ENV_PREFIX + "CLIENT_SECRET"), environment.get("CPI_CLIENT_SECRET")));
        override(values, "monitoringClientId", firstNonBlank(environment.get(ENV_PREFIX + "MONITORING_CLIENT_ID"), environment.get("CPI_MONITORING_ID")));
        override(values, "monitoringClientSecret", firstNonBlank(environment.get(ENV_PREFIX + "MONITORING_CLIENT_SECRET"), environment.get("CPI_MONITORING_SECRET")));
        override(values, "pollingIntervalMs", parseInteger(environment.get(ENV_PREFIX + "POLLING_INTERVAL_MS")));
        override(values, "pollingTimeoutMs", parseInteger(environment.get(ENV_PREFIX + "POLLING_TIMEOUT_MS")));
    }

    private void override(Map<String, Object> values, String key, Object value) {
        if (value != null) {
            values.put(key, value);
        }
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value.trim());
    }

    private CpiTestConfig mapConfig(Map<String, Object> values) {
        CpiTestConfig config = new CpiTestConfig(
                stringValue(values.get("monitoringBaseUrl")),
                stringValue(values.get("monitoringUser")),
                stringValue(values.get("monitoringPassword")),
                stringValue(values.get("inboundBaseUrl")),
                stringValue(values.get("inboundUser")),
                stringValue(values.get("inboundPassword")),
                stringValue(values.get("collectorBaseUrl")),
                integerValue(values.get("pollingIntervalMs")),
                integerValue(values.get("pollingTimeoutMs")),
                stringValue(values.get("tokenUrl")),
                stringValue(values.get("clientId")),
                stringValue(values.get("clientSecret")),
                stringValue(values.get("monitoringClientId")),
                stringValue(values.get("monitoringClientSecret")));
        validate(config);
        return config;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value).trim());
    }

    private void validate(CpiTestConfig config) {
        require(config.getMonitoringBaseUrl(), "monitoringBaseUrl");
        require(config.getCollectorBaseUrl(), "collectorBaseUrl");
        boolean hasBasicAuth = !isBlank(config.getMonitoringUser()) && !isBlank(config.getMonitoringPassword());
        if (!hasBasicAuth && !config.isMonitoringOAuth2Configured()) {
            throw new ConfigValidationException(
                    "Missing authentication configuration: provide either monitoringUser/monitoringPassword "
                            + "(Basic Auth) or tokenUrl/monitoringClientId/monitoringClientSecret (OAuth2 client credentials)");
        }
        if (config.getPollingIntervalMs() <= 0) {
            throw new ConfigValidationException("Invalid configuration field: pollingIntervalMs must be > 0");
        }
        if (config.getPollingTimeoutMs() <= 0) {
            throw new ConfigValidationException("Invalid configuration field: pollingTimeoutMs must be > 0");
        }
    }

    private void require(String value, String fieldName) {
        if (isBlank(value)) {
            throw new ConfigValidationException("Missing required configuration field: " + fieldName);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
