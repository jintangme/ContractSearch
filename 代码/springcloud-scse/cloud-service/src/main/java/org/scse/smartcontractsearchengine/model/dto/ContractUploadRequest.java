package org.scse.smartcontractsearchengine.model.dto;

import lombok.Data;

@Data
public class ContractUploadRequest {

    private String contractName;

    private String sourceCode;

    private String abi;

    private String category;

    private String description;
}
