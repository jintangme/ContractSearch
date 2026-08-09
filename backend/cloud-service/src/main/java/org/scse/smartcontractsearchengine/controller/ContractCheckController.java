package org.scse.smartcontractsearchengine.controller;

import org.scse.smartcontractsearchengine.common.result.ResponseResult;
import org.scse.smartcontractsearchengine.model.dto.ContractCheckRequest;
import org.scse.smartcontractsearchengine.model.vo.ContractCheckResponse;
import org.scse.smartcontractsearchengine.service.ContractCheckService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/contracts")
public class ContractCheckController {

    private final ContractCheckService contractCheckService;

    public ContractCheckController(ContractCheckService contractCheckService) {
        this.contractCheckService = contractCheckService;
    }

    @PostMapping("/check")
    public ResponseResult<ContractCheckResponse> checkContract(@RequestBody ContractCheckRequest request) {
        try {
            ContractCheckResponse result = contractCheckService.checkContract(
                    request.getContractName(),
                    request.getSourceCode(),
                    request.getInputMode(),
                    request.getFileName(),
                    request.getModel(),
                    !Boolean.FALSE.equals(request.getSaveRecord())
            );
            return ResponseResult.success(result);
        } catch (IllegalArgumentException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @PostMapping(value = "/check/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseResult<ContractCheckResponse> checkUploadedContract(
            @RequestParam(value = "contractName", required = false) String contractName,
            @RequestParam(value = "model", required = false) String model,
            @RequestPart("file") MultipartFile file
    ) {
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!fileName.matches("(?i).+\\.(sol|txt)$")) {
            return ResponseResult.fail("仅支持上传 .sol 或 .txt 源码文件");
        }

        try {
            String sourceCode = new String(file.getBytes(), StandardCharsets.UTF_8);
            ContractCheckResponse result = contractCheckService.checkContract(
                    contractName,
                    sourceCode,
                    "upload",
                    fileName,
                    model
            );
            return ResponseResult.success(result);
        } catch (IllegalArgumentException e) {
            return ResponseResult.fail(e.getMessage());
        } catch (Exception e) {
            return ResponseResult.fail("读取上传文件失败");
        }
    }
}
