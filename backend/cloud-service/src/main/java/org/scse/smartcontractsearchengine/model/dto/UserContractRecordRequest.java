package org.scse.smartcontractsearchengine.model.dto;

import lombok.Data;

@Data
public class UserContractRecordRequest {
    private String contractName;
    private String sourceCode;
    private String precheckConclusion;
    private String riskLevel;
    private Integer issueCount;
    private Object issues;
}
