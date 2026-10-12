package org.scse.cloudagent.tool;

import org.scse.cloudagent.domain.AgentErrorCode;

/** Run-owned accounting for an optional tool that calls the model. */
public final class ModelCallBudget {
    private final int limit;
    private int used;

    public ModelCallBudget(int limit) {
        if (limit < 0) throw new IllegalArgumentException("Negative model-call allowance");
        this.limit = limit;
    }

    public void consume() {
        if (used >= limit) throw new AgentToolException(AgentErrorCode.BUDGET_EXCEEDED,
                "No model-call allowance remains for optional review and final delivery");
        used++;
    }

    public int used() { return used; }

    /** Undo a reservation only when the provider adapter confirms no request was sent. */
    public void cancelUnsentRequest() {
        if (used == 0) throw new IllegalStateException("No reserved model call to cancel");
        used--;
    }
}
