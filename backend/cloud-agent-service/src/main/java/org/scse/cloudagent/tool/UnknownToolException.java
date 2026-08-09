package org.scse.cloudagent.tool;

public class UnknownToolException extends RuntimeException {
    public UnknownToolException(String name) {
        super("Unknown tool: " + name);
    }
}
