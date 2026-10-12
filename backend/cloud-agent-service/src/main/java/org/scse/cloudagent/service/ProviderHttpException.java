package org.scse.cloudagent.service;

import java.io.IOException;

/** Sanitized provider status; an adapter may attest that a request was not sent. */
public final class ProviderHttpException extends IOException {
    private final int statusCode;
    private final int providerCalls;

    public ProviderHttpException(int statusCode, int providerCalls) {
        super("Model provider returned HTTP " + statusCode);
        if (providerCalls < 0 || providerCalls > 1) throw new IllegalArgumentException("Invalid provider attempt count");
        this.statusCode = statusCode;
        this.providerCalls = providerCalls;
    }

    public int statusCode() { return statusCode; }
    public int providerCalls() { return providerCalls; }
}
