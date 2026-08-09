package org.scse.smartcontractsearchengine.service;

import org.scse.smartcontractsearchengine.model.vo.ContractCheckResponse;

public interface ContractCheckService {

    default ContractCheckResponse checkContract(String contractName, String sourceCode, String inputMode,
                                                String fileName, String model) {
        return checkContract(contractName, sourceCode, inputMode, fileName, model, true);
    }

    ContractCheckResponse checkContract(String contractName, String sourceCode, String inputMode,
                                        String fileName, String model, boolean saveRecord);
}
