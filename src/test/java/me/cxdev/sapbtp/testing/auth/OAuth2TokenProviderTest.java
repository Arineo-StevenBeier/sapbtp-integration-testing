package me.cxdev.sapbtp.testing.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.cxdev.sapbtp.testing.http.OkHttpTransport;
import okhttp3.Credentials;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class OAuth2TokenProviderTest {
    @Test
    void fetchesTokenUsingClientCredentialsGrant() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"access_token\":\"token-1\",\"expires_in\":3600}"));
            server.start();

            OAuth2TokenProvider provider = new OAuth2TokenProvider(
                    new OkHttpTransport(), server.url("/oauth/token").toString(), "client-id", "client-secret");

            String token = provider.getAccessToken();
            RecordedRequest request = server.takeRequest();

            assertEquals("token-1", token);
            assertEquals(Credentials.basic("client-id", "client-secret"), request.getHeader("Authorization"));
            assertEquals("grant_type=client_credentials", request.getBody().readUtf8());
        }
    }

    @Test
    void cachesTokenUntilExpiry() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"access_token\":\"token-1\",\"expires_in\":3600}"));
            server.start();

            OAuth2TokenProvider provider = new OAuth2TokenProvider(
                    new OkHttpTransport(), server.url("/oauth/token").toString(), "client-id", "client-secret");

            String first = provider.getAccessToken();
            String second = provider.getAccessToken();

            assertEquals("token-1", first);
            assertEquals("token-1", second);
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test
    void throwsWhenTokenEndpointFails() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(401).setBody("invalid client"));
            server.start();

            OAuth2TokenProvider provider = new OAuth2TokenProvider(
                    new OkHttpTransport(), server.url("/oauth/token").toString(), "client-id", "client-secret");

            assertThrows(IllegalStateException.class, provider::getAccessToken);
        }
    }
}
