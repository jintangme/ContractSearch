package org.scse.smartcontractsearchengine.service;

import org.scse.smartcontractsearchengine.model.dto.SmartContractSearchInputDto;
import org.scse.smartcontractsearchengine.model.entity.Group;
import org.scse.smartcontractsearchengine.model.entity.SmartContract;
import org.scse.smartcontractsearchengine.model.vo.ContractCardVo;
import org.scse.smartcontractsearchengine.model.vo.SmartContractSearchReturnVo;
import org.scse.smartcontractsearchengine.model.vo.ContractDetailVo;

import java.util.List;

public interface SearchService {

    List<ContractCardVo> search(SmartContractSearchInputDto dto);

    // SmartContractSearchReturnVo searchInFavorite(SmartContractSearchInputDto dto, Long userId);

    List<ContractCardVo> getAllContracts();

    ContractDetailVo getContractById(Long id);

    ContractDetailVo getContractById(Long id, boolean regenerateAbi);

    List<Group> getGroupList();

    List<ContractCardVo> getContractsByGroup(Long groupId);
}
