package org.scse.smartcontractsearchengine.model.dto;

import lombok.Data;

@Data
public class SearchResultDto {

    private Long id;

    private String address;

    private String contractName;

    private String sourceCode;

    private String category;

    private String description;

    private Double rrfScore;
}