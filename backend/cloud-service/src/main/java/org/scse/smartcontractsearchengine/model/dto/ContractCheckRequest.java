package org.scse.smartcontractsearchengine.model.dto;

import lombok.Data;

@Data
public class ContractCheckRequest {
    /**
     * 合约名称（可选）
     */
    private String contractName;

    /**
     * 用户直接粘贴的源码
     */
    private String sourceCode;

    /**
     * 输入方式：paste / upload
     */
    private String inputMode;

    /**
     * 上传文件名
     */
    private String fileName;

    /**
     * 用户选择的真实 AI 模型，例如 glm-5.1
     */
    private String model;
}
