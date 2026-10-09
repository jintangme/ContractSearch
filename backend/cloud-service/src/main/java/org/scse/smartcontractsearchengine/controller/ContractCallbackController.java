package org.scse.smartcontractsearchengine.controller;

import lombok.extern.slf4j.Slf4j;
import org.scse.smartcontractsearchengine.config.ContractImportPolicy;
import org.scse.smartcontractsearchengine.common.result.ResponseResult;
import org.scse.smartcontractsearchengine.model.dto.ContractFileNotificationDto;
import org.scse.smartcontractsearchengine.model.entity.SmartContract;
import org.scse.smartcontractsearchengine.service.ContractProcessService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/contracts")
public class ContractCallbackController {

    private final ContractProcessService contractProcessService;
    private final ContractImportPolicy importPolicy;

    public ContractCallbackController(ContractProcessService contractProcessService, ContractImportPolicy importPolicy) {
        this.contractProcessService = contractProcessService;
        this.importPolicy = importPolicy;
    }

    @PostMapping("/process")
    public ResponseResult<Void> processContracts(@RequestBody ContractFileNotificationDto notification,
            @RequestHeader(value = ContractImportPolicy.TOKEN_HEADER, required = false) String token) {
        Path approvedFile = importPolicy.authorizeAndResolve(token, notification.getFilePath());
        log.info("Processing authorized contract import, groupId={}", notification.getGroupId());

        List<SmartContract> priorityContracts = contractProcessService.processParquetFile(approvedFile.toString(), notification.getGroupId());

        if (!priorityContracts.isEmpty()) {
            contractProcessService.updateContractsDesc(priorityContracts);
            contractProcessService.updateContractsEmbedding(priorityContracts);
        }
        return ResponseResult.success();
    }
}
