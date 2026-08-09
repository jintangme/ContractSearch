package org.scse.smartcontractsearchengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SmartContractListUnitDto implements Serializable {

    private Long id;

    private String address;

    private String contractName;

    private String sourceCode;

    private String category;

    private String description;

    private Double hybridScore;

}
