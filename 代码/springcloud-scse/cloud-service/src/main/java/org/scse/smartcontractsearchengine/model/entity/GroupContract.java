package org.scse.smartcontractsearchengine.model.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GroupContract {
    private Long id;

    private Long groupId;

    private Long contractId;

    private Integer orderNum;
}
