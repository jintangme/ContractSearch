package org.scse.smartcontractsearchengine.model.dto;

import lombok.Data;
import org.scse.smartcontractsearchengine.model.entity.SmartContract;

import java.util.List;

@Data
public class GroupContractsDto {

    private Long groupId;

    private String groupTitle;

    private String groupDescription;

    private List<SmartContract> contracts;

}
