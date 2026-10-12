package org.scse.cloudagent.service;

import org.scse.cloudagent.domain.AgentErrorCode;
import org.scse.cloudagent.tool.AgentToolException;

/** A terminal provider failure retains the number of requests already attempted. */
public final class ProviderRequestFailure extends AgentToolException {
    private final int providerCalls;
    public ProviderRequestFailure(String message, int providerCalls, Throwable cause) {
        super(AgentErrorCode.PROVIDER_ERROR, message, cause);
        this.providerCalls = providerCalls;
    }
    public int providerCalls() { return providerCalls; }
}
