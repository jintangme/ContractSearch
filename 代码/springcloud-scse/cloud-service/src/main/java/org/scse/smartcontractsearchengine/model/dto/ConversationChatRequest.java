package org.scse.smartcontractsearchengine.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class ConversationChatRequest {
    private Long sessionId;
    private String model;
    private String message;
    private List<AttachmentPayload> attachments;

    @Data
    public static class AttachmentPayload {
        private String fileName;
        private String fileType;
        private String sourceType;
        private String contentText;
    }
}
