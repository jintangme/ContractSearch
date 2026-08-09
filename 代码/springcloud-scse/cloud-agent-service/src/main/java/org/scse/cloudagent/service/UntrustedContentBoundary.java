package org.scse.cloudagent.service;

import org.springframework.stereotype.Component;

@Component
public class UntrustedContentBoundary {
    public String wrap(String toolName, String jsonOutput) {
        String escaped = (jsonOutput == null ? "{}" : jsonOutput)
                .replace("<", "\\u003c")
                .replace(">", "\\u003e");
        return "<UNTRUSTED_TOOL_OUTPUT tool=\"" + toolName + "\">\n"
                + escaped
                + "\n</UNTRUSTED_TOOL_OUTPUT>";
    }
}
