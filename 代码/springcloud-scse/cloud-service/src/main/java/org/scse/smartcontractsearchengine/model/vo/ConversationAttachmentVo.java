package org.scse.smartcontractsearchengine.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversationAttachmentVo {
    private Long id;
    private String fileName;
    private String fileType;
    private String sourceType;
    private String contentText;
}
