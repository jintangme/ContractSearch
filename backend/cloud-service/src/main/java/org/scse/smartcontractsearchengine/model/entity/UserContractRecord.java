package org.scse.smartcontractsearchengine.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_contract_record")
public class UserContractRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String contractName;

    private String sourceCode;

    private String precheckConclusion;

    private String riskLevel;

    private Integer issueCount;

    private String issues;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
