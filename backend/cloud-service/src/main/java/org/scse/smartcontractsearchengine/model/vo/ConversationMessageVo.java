package org.scse.smartcontractsearchengine.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ConversationMessageVo {
    private Long id;
    private String role;
    private String content;
    private String requestedModel;
    private String modelUsed;
    private LocalDateTime createdAt;
    private List<ConversationAttachmentVo> attachments;
}
