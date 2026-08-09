package org.scse.smartcontractsearchengine.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.scse.smartcontractsearchengine.handler.VectorTypeHandler;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SmartContract {

    // 主键，插入后会自动回填
    @TableId(type = IdType.AUTO)
    private Long id;

    // 智能合约地址
    private String address;

    // 智能合约名称
    private String contractName;

    // 智能合约源代码
    private String sourceCode;

    // 智能合约 abi 接口
    private String abi;

    // ABI 生成状态：generated / failed / unavailable
    private String abiStatus;

    // ABI 生成失败原因
    private String abiErrorMessage;

    // 生成 ABI 时使用的 Solidity 编译器版本
    private String compilerVersion;

    // 智能合约字节码
    private String bytecode;

    // 智能合约类型
    private String category;

    // 大模型生成的描述
    private String description;

    @TableField(typeHandler = VectorTypeHandler.class)
    private float[] codeEmbedding;

    @TableField(typeHandler = VectorTypeHandler.class)
    private float[] descriptionEmbedding;

    @TableLogic(value = "false", delval = "true")
    private Boolean isDeleted;

    private Long uploaderId;

    private Boolean isEffective;


}
