package org.scse.smartcontractsearchengine.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.scse.smartcontractsearchengine.model.dto.SearchResultDto;
import org.scse.smartcontractsearchengine.model.dto.SmartContractListUnitDto;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmartContractSearchReturnVo {

    private Integer total;

    private List<SearchResultDto> results;

}
