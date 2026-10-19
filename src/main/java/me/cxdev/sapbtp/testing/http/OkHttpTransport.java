package me.cxdev.sapbtp.testing.http;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttpTransport implements HttpTransport {
    private final OkHttpClient client;

    public OkHttpTransport() {
        this(new OkHttpClient());
    }

    public OkHttpTransport(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public HttpResponse get(String url, Map<String, String> headers) {
        Request.Builder builder = new Request.Builder().url(url).get();
        headers.forEach(builder::addHeader);
        try (Response response = client.newCall(builder.build()).execute()) {
            return toResponse(response);
        } catch (IOException e) {
            throw new IllegalStateException("HTTP GET failed for " + url, e);
        }
    }

    @Override
    public HttpResponse post(String url, String body, Map<String, String> headers) {
        return post(url, body, headers, "application/json; charset=utf-8");
    }

    @Override
    public HttpResponse post(String url, String body, Map<String, String> headers, String contentType) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body == null ? "" : body, okhttp3.MediaType.parse(contentType)));
        headers.forEach(builder::addHeader);
        try (Response response = client.newCall(builder.build()).execute()) {
            return toResponse(response);
        } catch (IOException e) {
            throw new IllegalStateException("HTTP POST failed for " + url, e);
        }
    }

    @Override
    public HttpResponse delete(String url, Map<String, String> headers) {
        Request.Builder builder = new Request.Builder().url(url).delete();
        headers.forEach(builder::addHeader);
        try (Response response = client.newCall(builder.build()).execute()) {
            return toResponse(response);
        } catch (IOException e) {
            throw new IllegalStateException("HTTP DELETE failed for " + url, e);
        }
    }

    private HttpResponse toResponse(Response response) throws IOException {
        Map<String, String> responseHeaders = new LinkedHashMap<>();
        for (String name : response.headers().names()) {
            responseHeaders.put(name, response.header(name));
        }
        return new HttpResponse(
                response.code(),
                response.body() == null ? "" : response.body().string(),
                responseHeaders);
    }
}
