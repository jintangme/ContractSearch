package org.scse.smartcontractsearchengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SmartContractSearchInputDto implements Serializable {

    private String input; // 用户的输入

    private Integer limit; // 查询前top k个

    private String searchMode; // 查询方式

}
