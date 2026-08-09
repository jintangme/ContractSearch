package org.scse.smartcontractsearchengine.service;

import org.scse.smartcontractsearchengine.model.entity.SmartContract;

import java.util.List;

public interface ContractProcessService {

    /**
     * 处理 parquet 文件，存储元数据，插入或更新组
     * @param filePath parquet 文件路径
     * @param groupId 分组ID, 0表示无分组
     * @return 处理后需要优先更新描述的合约列表
     */
    List<SmartContract> processParquetFile(String filePath, Long groupId);

    /**
     * 调用updateAllContractsDesc()方法，更新所有合约的描述
     */
    void updateAllContractsDesc();

    /**
     * 调用updateAllContractsEmbedding()方法，更新所有合约的向量
     */
    void updateAllContractsEmbedding();

    /**
     * 调用updateContractsDesc()方法，更新所有无效合约的描述
     */
    void updateInEffectiveContractsDesc(int limit);

    /**
     * 调用updateContractsEmbedding()方法，更新所有无效合约的向量
     */
    void updateInEffectiveContractsEmbedding(int limit);

    void updateContractsDesc(List<SmartContract> contracts);

    void updateContractsEmbedding(List<SmartContract> contracts);
}
