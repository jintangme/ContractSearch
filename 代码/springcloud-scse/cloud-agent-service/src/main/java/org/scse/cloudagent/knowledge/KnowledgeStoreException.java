package org.scse.cloudagent.knowledge;

public class KnowledgeStoreException extends RuntimeException {
    public KnowledgeStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public KnowledgeStoreException(String message) {
        super(message);
    }
}
