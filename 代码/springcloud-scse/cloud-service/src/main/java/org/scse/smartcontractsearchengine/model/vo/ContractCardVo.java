package org.scse.smartcontractsearchengine.model.vo;

import lombok.Data;

@Data
public class ContractCardVo {

    private Long id;

    private String address;

    private String contractName;

    private String category;

    private String description;

    private String abiStatus;

    private String abiErrorMessage;

    private String compilerVersion;
}
