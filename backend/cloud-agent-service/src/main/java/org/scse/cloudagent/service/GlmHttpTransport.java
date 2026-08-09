package org.scse.cloudagent.service;

import java.io.IOException;
import java.net.http.HttpRequest;

public interface GlmHttpTransport {
    Response send(HttpRequest request) throws IOException, InterruptedException;

    record Response(int statusCode, String body) {
    }
}
