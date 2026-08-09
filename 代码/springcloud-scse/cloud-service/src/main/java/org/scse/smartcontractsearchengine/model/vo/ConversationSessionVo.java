package org.scse.smartcontractsearchengine.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ConversationSessionVo {
    private Long id;
    private String title;
    private String model;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ConversationMessageVo> messages;
}
