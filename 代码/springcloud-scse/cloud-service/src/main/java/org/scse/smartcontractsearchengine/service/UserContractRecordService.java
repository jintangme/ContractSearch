package org.scse.smartcontractsearchengine.service;

import org.scse.smartcontractsearchengine.model.dto.UserContractRecordRequest;
import org.scse.smartcontractsearchengine.model.vo.UserContractRecordVo;

import java.util.List;

public interface UserContractRecordService {
    UserContractRecordVo create(Long userId, String status, UserContractRecordRequest request);

    List<UserContractRecordVo> list(Long userId, String status);

    UserContractRecordVo get(Long userId, Long id, String status);

    void delete(Long userId, Long id, String status);
}
