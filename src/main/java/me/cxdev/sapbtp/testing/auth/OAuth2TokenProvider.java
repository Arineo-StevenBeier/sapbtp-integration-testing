package me.cxdev.sapbtp.testing.auth;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import me.cxdev.sapbtp.testing.http.HttpResponse;
import me.cxdev.sapbtp.testing.http.HttpTransport;
import okhttp3.Credentials;
import org.json.JSONObject;

public class OAuth2TokenProvider {
    private static final long EXPIRY_SAFETY_MARGIN_MS = 30_000;
    private static final long FALLBACK_EXPIRES_IN_SECONDS = 300;

    private final HttpTransport transport;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile String cachedToken;
    private volatile long expiresAtEpochMs;

    public OAuth2TokenProvider(HttpTransport transport, String tokenUrl, String clientId, String clientSecret) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.tokenUrl = Objects.requireNonNull(tokenUrl, "tokenUrl");
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.clientSecret = Objects.requireNonNull(clientSecret, "clientSecret");
    }

    public String getAccessToken() {
        String token = cachedToken;
        if (token != null && System.currentTimeMillis() < expiresAtEpochMs) {
            return token;
        }
        lock.lock();
        try {
            if (cachedToken != null && System.currentTimeMillis() < expiresAtEpochMs) {
                return cachedToken;
            }
            return requestNewToken();
        } finally {
            lock.unlock();
        }
    }

    private String requestNewToken() {
        Map<String, String> headers = Map.of(
                "Authorization", Credentials.basic(clientId, clientSecret),
                "Accept", "application/json");
        HttpResponse response = transport.post(
                tokenUrl, "grant_type=client_credentials", headers, "application/x-www-form-urlencoded; charset=utf-8");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OAuth2 token request failed with status " + response.statusCode()
                    + " for tokenUrl=" + tokenUrl + ": " + response.body());
        }
        JSONObject json;
        try {
            json = new JSONObject(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("OAuth2 token response was not valid JSON: " + response.body(), e);
        }
        String accessToken = json.optString("access_token", "");
        if (accessToken.isBlank()) {
            throw new IllegalStateException("OAuth2 token response did not contain an access_token: " + response.body());
        }
        long expiresInSeconds = json.optLong("expires_in", FALLBACK_EXPIRES_IN_SECONDS);
        cachedToken = accessToken;
        expiresAtEpochMs = System.currentTimeMillis() + Math.max(0, expiresInSeconds * 1000 - EXPIRY_SAFETY_MARGIN_MS);
        return accessToken;
    }
}
