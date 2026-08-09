package org.scse.smartcontractsearchengine.model.dto;

import lombok.Data;

@Data
public class ContractFileNotificationDto {
    private String filePath;
    private Long groupId;
}
