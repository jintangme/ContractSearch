package org.scse.smartcontractsearchengine.controller;

import lombok.extern.slf4j.Slf4j;
import org.scse.smartcontractsearchengine.common.result.ResponseResult;
import org.scse.smartcontractsearchengine.model.dto.ContractFileNotificationDto;
import org.scse.smartcontractsearchengine.model.entity.SmartContract;
import org.scse.smartcontractsearchengine.service.ContractProcessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/contracts")
public class ContractCallbackController {

    @Autowired
    private ContractProcessService contractProcessService;

    @PostMapping("/process")
    public ResponseResult<Void> processContracts(@RequestBody ContractFileNotificationDto notification) {
        log.info("Received contract file notification: path={}, groupId={}, timestamp={}",
                 notification.getFilePath(), notification.getGroupId(), LocalDateTime.now());

        List<SmartContract> priorityContracts = contractProcessService.processParquetFile(notification.getFilePath(), notification.getGroupId());

        if (!priorityContracts.isEmpty()) {
            contractProcessService.updateContractsDesc(priorityContracts);
            contractProcessService.updateContractsEmbedding(priorityContracts);
        }
        return ResponseResult.success();
    }
}
