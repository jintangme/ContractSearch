package org.scse.smartcontractsearchengine.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AttachmentParseResponse {
    private String fileName;
    private String fileType;
    private String contentText;
}
