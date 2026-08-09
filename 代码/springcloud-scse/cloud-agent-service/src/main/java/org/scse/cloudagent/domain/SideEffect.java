package org.scse.cloudagent.domain;

public enum SideEffect {
    NONE,
    READ,
    WRITE_REVERSIBLE,
    EXECUTE_SANDBOXED,
    EXTERNAL_NETWORK,
    IRREVERSIBLE
}
