package org.scse.smartcontractsearchengine.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractCheckResponse {

    /**
     * 是否像一个有效的 Solidity 合约
     */
    private Boolean validSolidity;

    /**
     * 是否结构基本完整
     */
    private Boolean structurallyComplete;

    /**
     * 风险等级：low / medium / high
     */
    private String riskLevel;

    /**
     * 是否建议收录：yes / review / no
     */
    private String recommendation;

    /**
     * 规则检查摘要
     */
    private String summary;

    /**
     * 发现的问题
     */
    private List<String> issues;

    /**
     * 基础统计信息
     */
    private Integer codeLength;

    /**
     * AI 预审结论
     */
    private String aiSummary;

    /**
     * 面向结果页的总评结论
     */
    private String conclusion;

    /**
     * AI 判断的合约类型
     */
    private String contractType;

    /**
     * 复杂度等级：low / medium / high
     */
    private String complexityLevel;

    /**
     * AI 给出的建议
     */
    private List<String> aiSuggestions;

    /**
     * 建议重点关注的函数、模块或关键词
     */
    private List<String> keyFocus;

    /**
     * 源码级批注式建议
     */
    private List<AnnotationSuggestion> annotations;

    /**
     * 固定风险维度评分
     */
    private RiskScores riskScores;

    /**
     * 是否已写入预审记录表
     */
    private Boolean saved;

    /**
     * 预审记录 ID
     */
    private Long recordId;

    private String provider;

    private String requestedModel;

    private String modelUsed;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnnotationSuggestion {
        private String target;
        private String problem;
        private String suggestion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskScores {
        private Integer permissionRisk;
        private Integer logicComplexityRisk;
        private Integer fundFlowRisk;
    }
}
