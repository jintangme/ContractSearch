package org.scse.smartcontractsearchengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {
    private boolean success;
    private String message;
    private String provider;
    private String requestedModel;
    private String modelUsed;

    public AiChatResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
