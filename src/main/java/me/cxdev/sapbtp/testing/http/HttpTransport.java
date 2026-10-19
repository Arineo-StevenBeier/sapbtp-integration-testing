package me.cxdev.sapbtp.testing.http;

import java.util.Map;

public interface HttpTransport {
    HttpResponse get(String url, Map<String, String> headers);

    default HttpResponse post(String url, String body, Map<String, String> headers) {
        return post(url, body, headers, "application/json; charset=utf-8");
    }

    HttpResponse post(String url, String body, Map<String, String> headers, String contentType);

    HttpResponse delete(String url, Map<String, String> headers);
}
