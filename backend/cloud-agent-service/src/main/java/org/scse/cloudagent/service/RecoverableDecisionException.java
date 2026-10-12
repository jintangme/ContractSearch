package org.scse.cloudagent.service;

import org.scse.cloudagent.domain.AgentErrorCode;
import org.scse.cloudagent.tool.AgentToolException;

/** A malformed model action is feedback; transport failures are not this type. */
public final class RecoverableDecisionException extends AgentToolException {
    private final int providerCalls;
    private final String rejectedMessage;
    public RecoverableDecisionException(String message, int providerCalls, String rejectedMessage, Throwable cause) {
        super(AgentErrorCode.PROVIDER_ERROR, message, cause);
        if (providerCalls < 1 || providerCalls > 2) throw new IllegalArgumentException("Invalid format-repair accounting");
        this.providerCalls = providerCalls;
        String raw = rejectedMessage == null ? "" : rejectedMessage;
        this.rejectedMessage = raw.substring(0, Math.min(8000, raw.length()));
    }
    public int providerCalls() { return providerCalls; }
    public String rejectedMessage() { return rejectedMessage; }
}
