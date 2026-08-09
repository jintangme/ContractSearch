package org.scse.smartcontractsearchengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SmartContractBaseDataDto implements Serializable {

    private Long id;

    private String address;

    private String contractName;

    private String sourceCode;

    private String category;

    private String description;

    private float[] codeEmbedding;

    private float[] descriptionEmbedding;

    private Boolean isEffective;

}
