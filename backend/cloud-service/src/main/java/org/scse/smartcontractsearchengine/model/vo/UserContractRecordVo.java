package org.scse.smartcontractsearchengine.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserContractRecordVo {
    private Long id;
    private String contractName;
    private String sourceCode;
    private String precheckConclusion;
    private String riskLevel;
    private Integer issueCount;
    private Object issues;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
