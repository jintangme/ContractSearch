package org.scse.smartcontractsearchengine.service.impl;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.scse.smartcontractsearchengine.common.enums.OpenAiClientType;
import org.scse.smartcontractsearchengine.common.ai.AiModelRegistry;
import org.scse.smartcontractsearchengine.config.OpenAIClientFactory;
import org.scse.smartcontractsearchengine.mapper.GroupContractMapper;
import org.scse.smartcontractsearchengine.mapper.SmartContractMapper;
import org.scse.smartcontractsearchengine.model.dto.SmartContractBaseDataDto;
import org.scse.smartcontractsearchengine.model.entity.GroupContract;
import org.scse.smartcontractsearchengine.model.entity.SmartContract;
import org.scse.smartcontractsearchengine.service.ContractProcessService;
import org.scse.smartcontractsearchengine.service.EmbeddingService;
import org.scse.smartcontractsearchengine.util.BeanPropertyBinder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ContractProcessServiceImpl implements ContractProcessService {

    @Autowired
    private OpenAIClientFactory aiClientFactory;

    @Autowired
    private SmartContractMapper smartContractMapper;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private GroupContractMapper groupContractMapper;

    @Override
    public List<SmartContract> processParquetFile(String filePath, Long groupId) {
        // 由于在组中，需要优先更新的合约
        List<SmartContract> priorityContracts = new ArrayList<>();
        Path file = new Path(filePath);
        try (ParquetReader<GenericRecord> reader =
                     AvroParquetReader.<GenericRecord>builder(file).build()) {
            GenericRecord record;
            int num = 1;
            while ((record = reader.read()) != null) {
                // 创建智能合约对象
                Schema schema = record.getSchema();
                SmartContract smartContract = new SmartContract();
                // 根据 schema 字段名，将 record 中的值赋给 smartContract 对象
                for (Schema.Field field : schema.getFields()) {
                    String fieldName = field.name();
                    Object fieldValue = record.get(fieldName);
                    BeanPropertyBinder.setField(smartContract, fieldName, fieldValue);
                }

                try {
                    // 插入智能合约
                    if (!verify(smartContract)) {
                        log.info("合约信息不完整，address: {}", smartContract.getAddress());
                        continue;
                    }
                    Long effectiveContractId;
                    if (smartContractMapper.isAddressExists(smartContract.getAddress()) == 0) {
                        smartContractMapper.insert(smartContract);
                        effectiveContractId = smartContract.getId();
                        log.info("已插入数据，id: {}", smartContract.getId());
                    }
                    else {
                        effectiveContractId = smartContractMapper.findIdByAddress(smartContract.getAddress());
                        if (effectiveContractId == null) {
                            log.warn("合约地址存在但未查询到记录，address: {}", smartContract.getAddress());
                            continue;
                        }
                        smartContract.setId(effectiveContractId);
                        log.info("该数据已存在，address: {} , id: {}", smartContract.getAddress(), effectiveContractId);
                    }
                    // 更新组
                    if (groupId != null && groupId != 0) {
                        try {
                            priorityContracts.add(smartContract);
                            groupContractMapper.upsertByGroupIdAndOrderNum(groupId, effectiveContractId, num);
                            log.info("已写入组关系，groupId: {}， contractId: {}， orderNum: {}", groupId, effectiveContractId, num);
                        } catch (Exception e) {
                            log.error("更新组失败，合约id: {}", smartContract.getId(), e);
                        }
                    }
                } catch (Exception e) {
                    log.error("插入智能合约失败，合约id: {}", smartContract.getId(), e);
                }
                num++;
            }
            log.info("已处理 {} 条数据", num);
        } catch (IOException e) {
            log.error("读取文件失败，文件路径: {}", filePath, e);
        }
        return priorityContracts;
    }

    @Override
    public void updateAllContractsDesc() {
        List<SmartContract> contracts = smartContractMapper.selectList(null);
        updateContractsDesc(contracts);
    }

    @Override
    public void updateAllContractsEmbedding() {
        List<SmartContract> contracts = smartContractMapper.selectList(null);
        updateContractsEmbedding(contracts);
    }

    @Override
    public void updateInEffectiveContractsDesc(int limit) {
        List<SmartContract> contracts = smartContractMapper.findInEffectiveContracts(limit);
        updateContractsDesc(contracts);
    }

    @Override
    public void updateInEffectiveContractsEmbedding(int limit) {
        List<SmartContract> contracts = smartContractMapper.findInEffectiveContracts(limit);
        updateContractsEmbedding(contracts);
    }

    @Override
    public void updateContractsDesc(List<SmartContract> contracts) {
        for (SmartContract contract : contracts) {
            try {
                String description = getNewDescription(contract, OpenAiClientType.ZHIPU);
                contract.setDescription(description);
                if (!descriptionValid(contract.getDescription())) {  // 合法性检查
                    throw new RuntimeException("合约描述不合法");
                }
                contract.setIsEffective(false); //  新合约描述，必须重新生成向量
                smartContractMapper.updateById(contract);
            } catch (RuntimeException e) {
                log.error("获取描述失败，合约id: {}", contract.getId(), e);
            }
        }
    }

    @Override
    public void updateContractsEmbedding(List<SmartContract> contracts) {
        for (SmartContract contract : contracts) {
            try {
                if (!descriptionValid(contract.getDescription())) {  // 合法性检查
                    throw new RuntimeException("合约描述不合法");
                }
                float[] embedding = embeddingService.getEmbeddings(List.of(contract.getDescription()), OpenAiClientType.LOCAL).get(0);
                contract.setDescriptionEmbedding(embedding);
                contract.setIsEffective(true);
                smartContractMapper.updateById(contract);
            } catch (RuntimeException e) {
                log.error("获取向量失败，合约id: {}", contract.getId(), e);
            }
        }
    }

    /**
     * 合约信息校验
     * @param smartContract 合约信息
     * @return 是否校验通过
     */
    private Boolean verify(SmartContract smartContract) {
        if (smartContract.getAddress() == null) return false;
        // if (smartContractMapper.isAddressExists(smartContract.getAddress()) > 0) return false;
        if (smartContract.getContractName() == null) return false;
        if (smartContract.getSourceCode() == null) return false;
        if (smartContract.getAbi() == null || smartContract.getAbi().equals("Contract source code not verified"))
            return false;
        return true;
    }

    /**
     * 调用大模型，获取描述
     *
     * @param dto
     * @return
     */
    private String getNewDescription(SmartContract dto, OpenAiClientType clientEnum) {
        String modelUsed = AiModelRegistry.DEFAULT_MODEL;
        String prompt = "请分析以下智能合约代码，并提供一个简短但准确的描述，包括：\n" +
                "1.合约名称\n" +
                "2.合约的主要功能\n" +
                "3.可能的应用场景\n" +
                "合约名称:\n" +
                dto.getContractName() + "\n" +
                "合约分类:\n" +
                dto.getCategory() + "\n" +
                "合约代码:\n" +
                dto.getSourceCode() + "\n" +
                "不要输出任何多余的文本,也不要使用markdown语法,请只分点返回文本描述";

        try {
            OpenAIClient client = aiClientFactory.getClient(clientEnum);

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(modelUsed)
                    .addUserMessage(prompt)
                    .temperature(0.9)
                    .topP(0.7)
                    .build();

            log.info("[AI] scene=description provider={} requestedModel={} modelUsed={}", AiModelRegistry.PROVIDER, null, modelUsed);
            ChatCompletion completion = client.chat().completions().create(params);
            String description = completion.choices().get(0).message().content().orElse("");
            log.info("获取描述成功，描述: {}", description);
            return description;
        } catch (Exception e) {
            log.error("获取描述失败，错误: {}", e.getMessage(), e);
            return dto.getDescription();
        }
    }

    /**
     * 判断描述是否合法
     *
     * @param description 描述
     * @return  是否合法
     */
    private Boolean descriptionValid(String description) {
        return description != null && !description.isEmpty();
    }
}
