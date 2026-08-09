package org.scse.smartcontractsearchengine.service;

import org.scse.smartcontractsearchengine.model.dto.ContractUploadRequest;
import org.scse.smartcontractsearchengine.model.vo.ContractCardVo;

import java.util.List;

public interface ContractUploadService {

    ContractCardVo uploadContract(ContractUploadRequest request);

    List<ContractCardVo> getMyUploadedContracts();

    void deleteMyUploadedContract(Long contractId);
}
