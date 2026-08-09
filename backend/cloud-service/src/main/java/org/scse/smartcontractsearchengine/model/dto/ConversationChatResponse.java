package org.scse.smartcontractsearchengine.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConversationChatResponse {
    private Boolean success;
    private String errorCode;
    private String message;
    private Long sessionId;
    private Long messageId;
    private String content;
    private String provider;
    private String requestedModel;
    private String modelUsed;
    private LocalDateTime createdAt;
}
